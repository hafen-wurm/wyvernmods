package mod.sin.wyvern;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.zones.NoSuchZoneException;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import mod.sin.lib.Util;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import java.util.logging.Logger;

public class QualityOfLife {
    public static Logger logger = Logger.getLogger(QualityOfLife.class.getName());

    public static boolean insertItemIntoVehicle(Item item, Item vehicle, Creature performer) {
        // If can put into crates, try that
        if (item.getTemplate().isBulk() && item.getRarity() == 0) {
            for (Item container : vehicle.getAllItems(true)) {
                if (container.getTemplateId() == ItemList.bulkContainer) {
                    if (container.getFreeVolume() >= item.getVolume()) {
                        if (item.AddBulkItem(performer, container)) {
                            performer.getCommunicator().sendNormalServerMessage(String.format("You put the %s in the %s in your %s.", item.getName(), container.getName(), vehicle.getName()));
                            return true;
                        }
                    }
                }
            }
            for (Item container : vehicle.getAllItems(true)) {
                if (container.isCrate() && container.canAddToCrate(item)) {
                    if (item.AddBulkItemToCrate(performer, container)) {
                        performer.getCommunicator().sendNormalServerMessage(String.format("You put the %s in the %s in your %s.", item.getName(), container.getName(), vehicle.getName()));
                        return true;
                    }
                }
            }
        }
        // No empty crates or disabled, try the vehicle itself
        if (vehicle.getNumItemsNotCoins() < 100 && vehicle.getFreeVolume() >= item.getVolume() && vehicle.insertItem(item)) {
            performer.getCommunicator().sendNormalServerMessage(String.format("You put the %s in the %s.", item.getName(), vehicle.getName()));
            return true;
        } else {
            // Send message if the vehicle is too full
            performer.getCommunicator().sendNormalServerMessage(String.format("The %s is too full to hold the %s.", vehicle.getName(), item.getName()));
            return false;
        }
    }
    public static Item getVehicleSafe(Creature pilot) {
        try {
            if (pilot.getVehicle() != -10)
                return Items.getItem(pilot.getVehicle());
        } catch (NoSuchItemException ignored) {
        }
        return null;
    }

    public static void vehicleHook(Creature performer, Item item){
        Item vehicleItem = getVehicleSafe(performer);
        if(vehicleItem != null && vehicleItem.isHollow()){
            if(insertItemIntoVehicle(item, vehicleItem, performer)){
                return;
            }
        }

        // Last resort, if no suitable vehicle is found.
        try {
            item.putItemInfrontof(performer);
        } catch (NoSuchCreatureException | NoSuchItemException | NoSuchPlayerException | NoSuchZoneException e) {
            e.printStackTrace();
        }
    }

    public static void preInit(){
        try{
            ClassPool classPool = HookManager.getInstance().getClassPool();
            final Class<QualityOfLife> thisClass = QualityOfLife.class;
            String replace;

            CtClass ctAction = classPool.get("com.wurmonline.server.behaviours.Action");
            CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
            CtClass ctTilePos = classPool.get("com.wurmonline.math.TilePos");
            CtClass ctCaveWallBehaviour = classPool.get("com.wurmonline.server.behaviours.CaveWallBehaviour");
            CtClass ctCaveTileBehaviour = classPool.get("com.wurmonline.server.behaviours.CaveTileBehaviour");
            CtClass[] params1 = {
                    ctAction,
                    ctCreature,
                    ctItem,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.booleanType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.shortType,
                    CtClass.floatType
            };
            CtClass[] mineParams = {
                    ctAction,
                    ctCreature,
                    ctItem,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.shortType,
                    CtClass.floatType,
                    CtClass.intType,
                    ctTilePos
            };
            CtClass[] flattenParams = {
                    ctCreature,
                    ctItem,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.floatType,
                    ctAction,
                    CtClass.intType
            };
            String flattenDesc = Descriptor.ofMethod(CtClass.booleanType, flattenParams);
            String mineDesc = Descriptor.ofMethod(CtClass.booleanType, mineParams);
            String desc1 = Descriptor.ofMethod(CtClass.booleanType, params1);
            if (WyvernMods.mineCaveToVehicle) {
                Util.setReason("Allow players to mine directly into vehicles.");
                replace = "$_ = null;"
                        + QualityOfLife.class.getName() + ".vehicleHook(performer, $0);";
                Util.instrumentDescribed(thisClass, ctCaveWallBehaviour, "action", desc1, "putItemInfrontof", replace);
                Util.instrumentDescribed(thisClass, ctCaveTileBehaviour, "mine", mineDesc, "putItemInfrontof", replace);
                Util.instrumentDescribed(thisClass, ctCaveTileBehaviour, "flatten", flattenDesc, "putItemInfrontof", replace);
            }

            CtClass ctTileRockBehaviour = classPool.get("com.wurmonline.server.behaviours.TileRockBehaviour");
            if (WyvernMods.mineSurfaceToVehicle) {
                Util.setReason("Allow players to surface mine directly into vehicles.");
                replace = "$_ = $proceed($$);" +
                        QualityOfLife.class.getName() + ".vehicleHook(performer, $0);";
                Util.instrumentDeclared(thisClass, ctTileRockBehaviour, "mine", "setDataXY", replace);
            }

            CtClass ctMethodsItems = classPool.get("com.wurmonline.server.behaviours.MethodsItems");
            if (WyvernMods.chopLogsToVehicle) {
                Util.setReason("Allow players to chop logs directly into vehicles.");
                replace = "$_ = null;" +
                        QualityOfLife.class.getName() + ".vehicleHook(performer, $0);";
                Util.instrumentDeclared(thisClass, ctMethodsItems, "chop", "putItemInfrontof", replace);
            }

            if (WyvernMods.statuetteAnyMaterial) {
                Util.setReason("Allow statuettes to be used when not gold/silver.");
                String desc100 = Descriptor.ofMethod(CtClass.booleanType, new CtClass[]{});
                replace = "{ return this.template.holyItem; }";
                Util.setBodyDescribed(thisClass, ctItem, "isHolyItem", desc100, replace);
            }

            /* Disabled in Wurm Unlimited 1.9 - Priest Rework changes removed this restriction.

            Util.setReason("Remove requirement for Libila priests to bless creatures before taming.");
            CtClass ctMethodsCreatures = classPool.get("com.wurmonline.server.behaviours.MethodsCreatures");
            replace = "$_ = false;";
            Util.instrumentDeclared(thisClass, ctMethodsCreatures, "tame", "isPriest", replace);*/

            if (WyvernMods.mineGemsToVehicle) {
                Util.setReason("Send gems, source crystals, flint, etc. into vehicle.");
                CtClass[] params2 = {
                        CtClass.intType,
                        CtClass.intType,
                        CtClass.intType,
                        CtClass.intType,
                        ctCreature,
                        CtClass.doubleType,
                        CtClass.booleanType,
                        ctAction
                };
                String desc2 = Descriptor.ofMethod(ctItem, params2);
                replace = "$_ = null;" +
                        QualityOfLife.class.getName() + ".vehicleHook(performer, $0);";
                Util.instrumentDescribed(thisClass, ctTileRockBehaviour, "createGem", desc2, "putItemInfrontof", replace);
            }

            if (WyvernMods.regenerateStaminaOnVehicleAnySlope) {
                CtClass ctPlayer = classPool.get("com.wurmonline.server.players.Player");
                ctPlayer.getMethod("poll", "()Z").instrument(new ExprEditor() {
                    @Override
                    public void edit(FieldAccess f) throws CannotCompileException {
                        if (f.getFieldName().equals("vehicle") && f.isReader())
                            f.replace("$_ = -10L;");
                    }
                });
            }

        } catch ( NotFoundException | IllegalArgumentException | ClassCastException e) {
            throw new HookException(e);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }
}
