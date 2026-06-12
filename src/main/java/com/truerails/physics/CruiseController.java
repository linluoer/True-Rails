package com.truerails.physics;

import com.mojang.logging.LogUtils;
import com.truerails.TRConfig;
import com.truerails.network.DriveInputPayload;
import com.truerails.registry.TRAttachments;
import com.truerails.registry.TRSounds;
import com.truerails.train.TrainData;
import com.truerails.train.TrainRuntime;
import com.truerails.train.TrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * 巡航控制器——唯一物理权威（M9）。
 * 1.21.1 矿车无 controllingPassenger，客户端不发载具移动包，
 * 服务端统一模拟载人与无人列车；驾驶输入经 DriveInputPayload 上行。
 * 速度上限由 Mixin(getMaxSpeedWithRail) 硬解锁；
 * 写回预除 0.75 补偿原版"载人矿车每刻 ×0.75"阻尼（M9fix）。
 */
public final class CruiseController {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean DEBUG = false;
    private static final double DT = 0.05;
    private static final int MIN_DWELL = 10;
    private static final double GRACE = 8.0;
    private static final double DOCK_SPEED = 1.5;
    private static final int LOW_FUEL_REPEAT = 600;

    private static String f(double v) { return v > 900 ? "inf" : String.format("%.2f", v); }

    public static void tick(ServerLevel level, AbstractMinecart head, List<AbstractMinecart> train) {
        TrainRuntime rt = TrainRuntime.get(head);
        Vec3 posNow = head.position();
        double measured = rt.lastPos == null
                ? head.getDeltaMovement().horizontalDistance() * 20.0
                : Math.hypot(posNow.x - rt.lastPos.x, posNow.z - rt.lastPos.z) * 20.0;
        rt.lastPos = posNow;
        double speed = Math.min(measured, TRConfig.ABSOLUTE_CAP.get());

        Player driver = findDriver(level, head, train);
        TrainData data = head.getData(TRAttachments.TRAIN_DATA);
        boolean furnaceHead = head instanceof MinecartFurnace;
        boolean fueled = furnaceHead && data.fuel > 0.0;
        if (driver == null && !fueled) return;

        BlockPos railPos = TrackScanner.findRail(level, head.blockPosition());
        if (railPos == null) return;

        BlockState railState = level.getBlockState(railPos);
        RailShape shape = railState.getValue(((BaseRailBlock) railState.getBlock()).getShapeProperty());

        boolean onPoweredRailBlock = railState.is(Blocks.POWERED_RAIL);
        boolean onStopRail = onPoweredRailBlock && !railState.getValue(PoweredRailBlock.POWERED);
        boolean onChargedRail = onPoweredRailBlock && railState.getValue(PoweredRailBlock.POWERED);
        if (!onPoweredRailBlock) rt.forcePassWhistled = false;

        boolean fwd = (rt.inputMask & DriveInputPayload.FWD) != 0;
        boolean back = (rt.inputMask & DriveInputPayload.BACK) != 0;
        boolean braking = (rt.inputMask & DriveInputPayload.BRAKE) != 0;

        TrainState st = TrainState.byOrdinal(data.state);

        // ============ DOCKED ============
        if (st == TrainState.DOCKED) {
            head.setDeltaMovement(0.0, head.getDeltaMovement().y, 0.0);
            rt.dwellTicks++;
            boolean railCharged = anyCartOnChargedRail(level, train);
            boolean mannedDepart = driver != null && fwd;
            boolean fuelOk = driver != null || fueled;
            if ((railCharged || mannedDepart) && rt.dwellTicks >= MIN_DWELL && fuelOk) {
                if (data.cruise < 4.0) data.cruise = 8.0;
                data.state = TrainState.DEPARTING.ordinal();
                rt.graceBlocks = GRACE;
                rt.lastBoostRail = null;
                level.playSound(null, head.blockPosition(),
                        TRSounds.WHISTLE_SHORT.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
            }
            return;
        }

        // ============ DEPARTING 宽限 ============
        boolean inGrace = st == TrainState.DEPARTING;
        if (inGrace) {
            if (onPoweredRailBlock) {
                rt.graceBlocks = GRACE;
            } else {
                rt.graceBlocks -= speed * DT;
                if (rt.graceBlocks <= 0) {
                    data.state = TrainState.CRUISING.ordinal();
                    inGrace = false;
                }
            }
        }

        boolean forceThrough = driver != null && fwd;
        boolean stopActive = !inGrace && !forceThrough;

        if (forceThrough && onStopRail && !rt.forcePassWhistled) {
            rt.forcePassWhistled = true;
            level.playSound(null, head.blockPosition(),
                    TRSounds.WHISTLE_LONG.get(), SoundSource.NEUTRAL, 1.4f, 1.0f);
        }

        // ============ 停靠触发 ============
        if (stopActive && onStopRail && speed < DOCK_SPEED) {
            head.setPos(railPos.getX() + 0.5, head.getY(), railPos.getZ() + 0.5);
            head.setDeltaMovement(0.0, head.getDeltaMovement().y, 0.0);
            data.state = TrainState.DOCKED.ordinal();
            rt.dwellTicks = 0;
            rt.boostExtra = 0.0;
            return;
        }

        double accel = TRConfig.ACCELERATION.get();
        double brake = TRConfig.BRAKE_FORCE.get();
        double eBrake = TRConfig.EMERGENCY_BRAKE.get();
        double maxSpeed = fueled ? TRConfig.MAX_SPEED.get()
                : (driver != null ? TRConfig.MANUAL_DRIVE_LIMIT.get() : 0.0);

        // —— 巡航目标：有人 W/S/空格；无人车 cruise=GUI 档位 ——
        if (driver != null) {
            if (braking) {
                data.cruise = 0.0;
                rt.boostExtra = 0.0;
            } else if (fwd) {
                data.cruise = Math.min(Math.max(data.cruise, speed) + accel * DT, maxSpeed);
            } else if (back) {
                data.cruise = Math.max(Math.min(data.cruise, speed) - brake * DT, 0.0);
            }
        }

        // —— 低燃料警示 + 黑烟 ——
        if (furnaceHead) {
            double pct = data.fuel / TRConfig.FUEL_CAPACITY.get();
            if (pct > 0.0 && pct < 0.10) {
                if (rt.lowFuelCooldown <= 0) {
                    level.playSound(null, head.blockPosition(),
                            TRSounds.LOW_FUEL.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                    rt.lowFuelCooldown = LOW_FUEL_REPEAT;
                }
                if (head.tickCount % 8 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            head.getX(), head.getY() + 0.9, head.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
                }
            } else {
                rt.lowFuelCooldown = 0;
            }
            if (rt.lowFuelCooldown > 0) rt.lowFuelCooldown--;
        }

        // —— 动力轨冲量 ——
        if (onChargedRail && !railPos.equals(rt.lastBoostRail)) {
            rt.boostExtra += TRConfig.BOOST_IMPULSE.get();
            rt.lastBoostRail = railPos.immutable();
        }
        rt.boostExtra *= Math.exp(-0.6931471805599453 / TRConfig.OVERSPEED_HALF_LIFE.get() * DT);
        if (rt.boostExtra < 0.05) rt.boostExtra = 0.0;

        boolean logTick = DEBUG && head.tickCount % 20 == 0;
        if ((head.tickCount & 3) == 0) {
            rt.envLimit = computeEnvelope(level, head, shape, speed, brake, stopActive, onStopRail, logTick);
        }

        double cruise = Math.min(data.cruise, maxSpeed);
        double target = Math.min(cruise + rt.boostExtra,
                Math.min(rt.envLimit, TRConfig.ABSOLUTE_CAP.get()));
        if (target < cruise + rt.boostExtra) {
            rt.boostExtra = Math.max(0.0, target - cruise);
        }

        double newSpeed;
        if (speed < target) {
            newSpeed = Math.min(speed + accel * DT, target);
        } else {
            newSpeed = Math.max(speed - (braking ? eBrake : brake) * DT, Math.max(target, 0.0));
        }

        // —— 油耗 ∝ 速度平方 ——
        if (furnaceHead && newSpeed > 0.1 && data.fuel > 0.0) {
            data.fuel = Math.max(0.0,
                    data.fuel - TRConfig.FUEL_RATE.get() * (newSpeed * newSpeed) / (48.0 * 48.0));
        }

        // —— 粒子 ——
        if (braking && newSpeed > 4.0 && head.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.LAVA,
                    head.getX(), head.getY() + 0.1, head.getZ(), 2, 0.4, 0.05, 0.4, 0.0);
        } else if (newSpeed > TRConfig.MAX_SPEED.get() + 0.5 && head.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.CRIT,
                    head.getX(), head.getY() + 0.1, head.getZ(), 3, 0.4, 0.05, 0.4, 0.1);
        }

        if (!inGrace) {
            data.state = (braking || newSpeed > target + 0.5)
                    ? TrainState.BRAKING.ordinal() : TrainState.CRUISING.ordinal();
        }

        if (logTick) {
            LOGGER.info("[TrueRails/S] speed={} new={} cruise={} target={} env={} boost={} stopRail={} state={} fuel={}",
                    f(speed), f(newSpeed), f(data.cruise), f(target), f(rt.envLimit),
                    f(rt.boostExtra), onStopRail, TrainState.byOrdinal(data.state), f(data.fuel));
        }

        // —— 写回（服务端唯一权威）——
        Vec3 motion = head.getDeltaMovement();
        Vec3 dir;
        if (Math.hypot(motion.x, motion.z) > 1.0e-4) {
            dir = new Vec3(motion.x, 0.0, motion.z).normalize();
        } else if (furnaceHead && train.size() > 1) {
            Vec3 away = head.position().subtract(train.get(1).position());
            dir = snapToRail(new Vec3(away.x, 0, away.z), shape);
        } else if (driver != null) {
            dir = snapToRail(driver.getLookAngle(), shape);
        } else {
            dir = snapToRail(Vec3.directionFromRotation(0.0F, head.getYRot()), shape);
        }

        if (newSpeed < 0.02) {
            head.setDeltaMovement(0.0, motion.y, 0.0);
            return;
        }
        double bpt = newSpeed / 20.0;
        // M9fix: 原版 moveAlongTrack 对载人矿车(isVehicle)每刻水平动量 ×0.75
        // （日志平衡点 1.05 = 0.75×(1.05+0.35) 实证）。预除补偿，
        // 原版乘回 0.75 后实际位移正好 = newSpeed。
        double comp = head.isVehicle() ? bpt / 0.75 : bpt;
        head.setDeltaMovement(dir.x * comp, motion.y, dir.z * comp);
    }

    public static boolean anyCartOnChargedRail(Level level, List<AbstractMinecart> train) {
        for (AbstractMinecart c : train) {
            BlockPos rp = TrackScanner.findRail(level, c.blockPosition());
            if (rp == null) continue;
            BlockState s = level.getBlockState(rp);
            if (s.is(Blocks.POWERED_RAIL) && s.getValue(PoweredRailBlock.POWERED)) return true;
        }
        return false;
    }

    @Nullable
    private static Player findDriver(ServerLevel level, AbstractMinecart head, List<AbstractMinecart> train) {
        TrainRuntime rt = TrainRuntime.get(head);
        if (rt.driver != null) {
            Player p = level.getPlayerByUUID(rt.driver);
            if (p != null && p.getVehicle() instanceof AbstractMinecart v && train.contains(v)) return p;
        }
        for (AbstractMinecart c : train) {
            for (Entity e : c.getPassengers()) {
                if (e instanceof Player p) return p;
            }
        }
        return null;
    }

    private static double computeEnvelope(ServerLevel level, AbstractMinecart cart,
                                          RailShape currentShape, double speed, double brake,
                                          boolean stopActive, boolean onStopRail, boolean log) {
        double curveLimit = TRConfig.CURVE_LIMIT.get();
        double slopeLimit = TRConfig.SLOPE_LIMIT.get();
        double margin = TRConfig.BRAKE_SAFETY_MARGIN.get();
        double env = Double.MAX_VALUE;

        if (isCurve(currentShape)) env = Math.min(env, curveLimit);
        if (currentShape.isAscending()) env = Math.min(env, slopeLimit);
        if (stopActive && onStopRail) env = Math.min(env, DOCK_SPEED - 0.1);

        Vec3 motion = cart.getDeltaMovement();
        if (Math.hypot(motion.x, motion.z) < 1.0e-4) return env;

        int range = Math.min((int) Math.ceil(speed * speed / (2.0 * brake)) + 12, 128);
        TrackScanner.ScanResult scan = TrackScanner.scan(level, cart, motion, range);

        if (log) {
            LOGGER.info("[TrueRails/S] scan: curve={} slope={} stop={} range={} shape={}",
                    scan.distCurve(), scan.distSlope(), scan.distStop(), range, currentShape);
        }

        if (scan.distCurve() >= 0) {
            double d = Math.max(0.0, scan.distCurve() - margin);
            env = Math.min(env, Math.sqrt(curveLimit * curveLimit + 2.0 * brake * d));
        }
        if (scan.distSlope() >= 0) {
            double d = Math.max(0.0, scan.distSlope() - margin);
            env = Math.min(env, Math.sqrt(slopeLimit * slopeLimit + 2.0 * brake * d));
        }
        if (stopActive && scan.distStop() >= 0) {
            double d = Math.max(0.0, scan.distStop() - 0.5);
            env = Math.min(env, Math.max(1.0, Math.sqrt(2.0 * brake * d)));
        }
        return env;
    }

    public static boolean isCurve(RailShape s) {
        return s == RailShape.SOUTH_EAST || s == RailShape.SOUTH_WEST
                || s == RailShape.NORTH_WEST || s == RailShape.NORTH_EAST;
    }

    public static Vec3 snapToRail(Vec3 want, RailShape shape) {
        boolean ew = shape == RailShape.EAST_WEST
                || shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST;
        boolean ns = shape == RailShape.NORTH_SOUTH
                || shape == RailShape.ASCENDING_NORTH || shape == RailShape.ASCENDING_SOUTH;
        if (ew) return new Vec3(want.x >= 0 ? 1 : -1, 0, 0);
        if (ns) return new Vec3(0, 0, want.z >= 0 ? 1 : -1);
        Vec3 flat = new Vec3(want.x, 0, want.z);
        return flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private CruiseController() {}
}
