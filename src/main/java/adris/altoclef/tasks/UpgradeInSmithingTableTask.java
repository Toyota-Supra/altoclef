package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.slot.ClickSlotTask;
import adris.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import adris.altoclef.tasks.slot.MoveItemToSlotTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.csharpisbetter.TimerGame;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.slots.SmithingTableSlot;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class UpgradeInSmithingTableTask extends ResourceTask {

    private final ItemTarget _tool;
    private final ItemTarget _material;
    private final ItemTarget _output;

    private final Task _innerTask;

    public UpgradeInSmithingTableTask(ItemTarget tool, ItemTarget material, ItemTarget output) {
        super(output);
        _tool = new ItemTarget(tool, output.getTargetCount());
        _material = new ItemTarget(material, output.getTargetCount());
        _output = output;
        _innerTask = new UpgradeInSmithingTableInternalTask();
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    private int getItemsInSlot(AltoClef mod, Slot slot, ItemTarget match) {
        ItemStack stack = mod.getInventoryTracker().getItemStackInSlot(slot);
        if (!stack.isEmpty() && match.matches(stack.getItem())) {
            return stack.getCount();
        }
        return 0;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // if we don't have tools + materials, get them.

        boolean inSmithingTable = (mod.getPlayer().currentScreenHandler instanceof SmithingScreenHandler);

        int materialsInSlot = inSmithingTable ? getItemsInSlot(mod, SmithingTableSlot.INPUT_SLOT_MATERIALS, _material) : 0;
        int toolsInSlot = inSmithingTable ? getItemsInSlot(mod, SmithingTableSlot.INPUT_SLOT_TOOL, _tool) : 0;
        int ouputInSlot = inSmithingTable ? getItemsInSlot(mod, SmithingTableSlot.OUTPUT_SLOT, _output) : 0;

        int desiredOutput = _output.getTargetCount() - ouputInSlot;

        if (mod.getInventoryTracker().getItemCount(_tool) + toolsInSlot < desiredOutput ||
                mod.getInventoryTracker().getItemCount(_material) + materialsInSlot < desiredOutput) {
            setDebugState("Getting materials + tools");
            return TaskCatalogue.getSquashedItemTask(_tool, _material);
        }

        // Edge case: We are wearing the armor we want to upgrade. If so, remove it.
        if (mod.getInventoryTracker().isArmorEquipped(_tool.getMatches())) {
            // Exit out of any screen so we can move our armor
            if (!(mod.getPlayer().currentScreenHandler instanceof PlayerScreenHandler)) {
                mod.getControllerExtras().closeScreen();
                setDebugState("Quickly removing equipped armor");
                return null;
            }
            // Take off our armor
            if (mod.getInventoryTracker().isInventoryFull()) {
                return new EnsureFreeInventorySlotTask();
            }
            for (Slot armorSlot : PlayerSlot.ARMOR_SLOTS) {
                if (_tool.matches(mod.getInventoryTracker().getItemStackInSlot(armorSlot).getItem())) {
                    setDebugState("Quickly removing equipped armor");
                    return new ClickSlotTask(armorSlot, 0, SlotActionType.QUICK_MOVE);
                }
            }
        }

        setDebugState("Smithing...");
        return _innerTask;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof UpgradeInSmithingTableTask task) {
            return task._tool.equals(_tool) && task._output.equals(_output) && task._material.equals(_material);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Upgrading " + _tool.toString() + " + " + _material.toString() + " -> " + _output.toString();
    }

    private class UpgradeInSmithingTableInternalTask extends DoStuffInContainerTask {

        private final TimerGame _invTimer;

        public UpgradeInSmithingTableInternalTask() {
            super(Blocks.SMITHING_TABLE, "smithing_table");
            _invTimer = new TimerGame(0);
        }

        @Override
        protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
            // inner part, don't care
            return true;
        }

        @Override
        protected boolean isContainerOpen(AltoClef mod) {
            return (mod.getPlayer().currentScreenHandler instanceof SmithingScreenHandler);
        }

        @Override
        protected Task containerSubTask(AltoClef mod) {
            setDebugState("Smithing...");
            // We have our tools + materials. Now, do the thing.
            _invTimer.setInterval(mod.getModSettings().getContainerItemMoveDelay());

            // Run once every
            if (!_invTimer.elapsed()) {
                return null;
            }
            _invTimer.reset();

            Slot materialSlot = SmithingTableSlot.INPUT_SLOT_MATERIALS;
            Slot toolSlot = SmithingTableSlot.INPUT_SLOT_TOOL;
            Slot outputSlot = SmithingTableSlot.OUTPUT_SLOT;

            ItemStack currentMaterials = mod.getInventoryTracker().getItemStackInSlot(materialSlot);
            ItemStack currentTools = mod.getInventoryTracker().getItemStackInSlot(toolSlot);
            ItemStack currentOutput = mod.getInventoryTracker().getItemStackInSlot(outputSlot);
            // Grab from output
            if (!currentOutput.isEmpty()) {
                return new ClickSlotTask(outputSlot, SlotActionType.QUICK_MOVE);
            }
            // Put materials in slot
            if (currentMaterials.isEmpty() || !_material.matches(currentMaterials.getItem())) {
                return new MoveItemToSlotTask(new ItemTarget(_material, 1), materialSlot);
            }
            // Put tool in slot
            if (currentTools.isEmpty() || !_tool.matches(currentTools.getItem())) {
                return new MoveItemToSlotTask(new ItemTarget(_tool, 1), toolSlot);
            }

            setDebugState("PROBLEM: Nothing to do!");
            return null;
        }

        @Override
        protected double getCostToMakeNew(AltoClef mod) {
            int price = 400;
            if (mod.getInventoryTracker().hasItem(ItemHelper.LOG) || mod.getInventoryTracker().getItemCount(ItemHelper.PLANKS) >= 4) {
                price -= 125;
            }
            if (mod.getInventoryTracker().getItemCount(Items.FLINT) >= 2) {
                price -= 125;
            }
            return price;
        }
    }

    public ItemTarget getTools() {
        return _tool;
    }

    public ItemTarget getMaterials() {
        return _material;
    }

}
