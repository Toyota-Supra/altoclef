package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Finds the closest reachable block and passes that block to a task.
 */
public class DoToClosestBlockTask extends AbstractDoToClosestObjectTask<BlockPos> {

    private final Block[] _targetBlocks;

    private final Supplier<Vec3d> _getOriginPos;
    private final Function<Vec3d, BlockPos> _getClosest;

    private final Function<BlockPos, Task> _getTargetTask;


    public DoToClosestBlockTask(Supplier<Vec3d> getOriginSupplier, Function<BlockPos, Task> getTargetTask, Function<Vec3d, BlockPos> getClosestBlock, Block... blocks) {
        _getOriginPos = getOriginSupplier;
        _getTargetTask = getTargetTask;
        _targetBlocks = blocks;
        _getClosest = getClosestBlock;
    }

    public DoToClosestBlockTask(Function<BlockPos, Task> getTargetTask, Function<Vec3d, BlockPos> getClosestBlock, Block... blocks) {
        this(null, getTargetTask, getClosestBlock, blocks);
    }

    public DoToClosestBlockTask(Function<BlockPos, Task> getTargetTask, Block... blocks) {
        this(getTargetTask, null, blocks);
    }

    @Override
    protected Vec3d getPos(AltoClef mod, BlockPos obj) {
        return new Vec3d(obj.getX(), obj.getY(), obj.getZ());
    }

    @Override
    protected BlockPos getClosestTo(AltoClef mod, Vec3d pos) {
        if (_getClosest != null) {
            return _getClosest.apply(pos);
        }
        return mod.getBlockTracker().getNearestTracking(pos, _targetBlocks);
    }

    @Override
    protected Vec3d getOriginPos(AltoClef mod) {
        if (_getOriginPos != null) {
            return _getOriginPos.get();
        }
        return mod.getPlayer().getPos();
    }

    @Override
    protected Task getGoalTask(BlockPos obj) {
        return _getTargetTask.apply(obj);
    }

    @Override
    protected boolean isValid(AltoClef mod, BlockPos obj) {
        // Assume we're valid since we're in the same chunk.
        if (!mod.getChunkTracker().isChunkLoaded(obj)) return true;

        return mod.getBlockTracker().blockIsValid(obj, _targetBlocks);
    }

    @Override
    protected void onStart(AltoClef mod) {
        mod.getBlockTracker().trackBlock(_targetBlocks);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(_targetBlocks);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DoToClosestBlockTask task) {
            return Arrays.equals(task._targetBlocks, _targetBlocks);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Doing something to closest block...";
    }
}