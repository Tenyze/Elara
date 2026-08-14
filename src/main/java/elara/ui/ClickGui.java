package elara.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elara.Elara;
import elara.module.Module;
import elara.module.combat.*;
import elara.module.combat.AutoHeal;
import elara.module.movement.*;
import elara.module.render.*;
import elara.module.utility.*;
import elara.module.world.*;
import elara.module.exploit.*;
import elara.module.misc.*;
import elara.ui.components.BindComponent;
import elara.ui.components.CategoryComponent;
import elara.ui.components.ModuleComponent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ClickGui extends GuiScreen {
    private static ClickGui instance;
    private final File configFile = new File("./config/elara/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;

    public ClickGui() {
        instance = this;

        List<Module> combatModules = new ArrayList<>();
        combatModules.add(Elara.moduleManager.getModule(Teams.class));
        combatModules.add(Elara.moduleManager.getModule(AimAssist.class));
        combatModules.add(Elara.moduleManager.getModule(AutoClicker.class));
        combatModules.add(Elara.moduleManager.getModule(KillAura.class));
        combatModules.add(Elara.moduleManager.getModule(Wtap.class));
        combatModules.add(Elara.moduleManager.getModule(Knockback.class));
        combatModules.add(Elara.moduleManager.getModule(KnockbackLegacy.class));
        combatModules.add(Elara.moduleManager.getModule(Reach.class));
        combatModules.add(Elara.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(Elara.moduleManager.getModule(NoHitDelay.class));
        combatModules.add(Elara.moduleManager.getModule(AntiFireball.class));
        combatModules.add(Elara.moduleManager.getModule(LagRange.class));
        combatModules.add(Elara.moduleManager.getModule(HitBox.class));
        combatModules.add(Elara.moduleManager.getModule(MoreKB.class));
        combatModules.add(Elara.moduleManager.getModule(Refill.class));
        combatModules.add(Elara.moduleManager.getModule(HitSelect.class));
        combatModules.add(Elara.moduleManager.getModule(BlockHit.class));
        combatModules.add(Elara.moduleManager.getModule(Criticals.class));
        combatModules.add(Elara.moduleManager.getModule(Hitflick.class));
        combatModules.add(Elara.moduleManager.getModule(KnockbackDelay.class));
        combatModules.add(Elara.moduleManager.getModule(Piercing.class));
        combatModules.add(Elara.moduleManager.getModule(SuperKnockback.class));
        combatModules.add(Elara.moduleManager.getModule(SmartAttack.class));
        combatModules.add(Elara.moduleManager.getModule(SprintReset.class));
        combatModules.add(Elara.moduleManager.getModule(Displace.class));
        combatModules.add(Elara.moduleManager.getModule(AutoHeal.class));
        combatModules.add(Elara.moduleManager.getModule(AutoProjectiles.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(Elara.moduleManager.getModule(AutoMLG.class));
        movementModules.add(Elara.moduleManager.getModule(Fly.class));
        movementModules.add(Elara.moduleManager.getModule(Speed.class));
        movementModules.add(Elara.moduleManager.getModule(LongJump.class));
        movementModules.add(Elara.moduleManager.getModule(Sprint.class));
        movementModules.add(Elara.moduleManager.getModule(SafeWalk.class));
        movementModules.add(Elara.moduleManager.getModule(Jesus.class));
        movementModules.add(Elara.moduleManager.getModule(Blink.class));
        movementModules.add(Elara.moduleManager.getModule(NoFall.class));
        movementModules.add(Elara.moduleManager.getModule(NoSlow.class));
        movementModules.add(Elara.moduleManager.getModule(KeepSprint.class));
        movementModules.add(Elara.moduleManager.getModule(Eagle.class));
        movementModules.add(Elara.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(Elara.moduleManager.getModule(AntiVoid.class));
        movementModules.add(Elara.moduleManager.getModule(Stasis.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(Elara.moduleManager.getModule(ESP.class));
        renderModules.add(Elara.moduleManager.getModule(ShaderESP.class));
        renderModules.add(Elara.moduleManager.getModule(ItemGlow.class));
        renderModules.add(Elara.moduleManager.getModule(Chams.class));
        renderModules.add(Elara.moduleManager.getModule(FreeLook.class));
        renderModules.add(Elara.moduleManager.getModule(FullBright.class));
        renderModules.add(Elara.moduleManager.getModule(Tracers.class));
        renderModules.add(Elara.moduleManager.getModule(NameTags.class));
        renderModules.add(Elara.moduleManager.getModule(Xray.class));
        renderModules.add(Elara.moduleManager.getModule(TargetHUD.class));
        renderModules.add(Elara.moduleManager.getModule(CombatVisuals.class));
        renderModules.add(Elara.moduleManager.getModule(Indicators.class));
        renderModules.add(Elara.moduleManager.getModule(BedESP.class));
        renderModules.add(Elara.moduleManager.getModule(ItemESP.class));
        renderModules.add(Elara.moduleManager.getModule(ViewClip.class));
        renderModules.add(Elara.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(Elara.moduleManager.getModule(HUD.class));
        renderModules.add(Elara.moduleManager.getModule(GuiModule.class));
        renderModules.add(Elara.moduleManager.getModule(ChestESP.class));
        renderModules.add(Elara.moduleManager.getModule(Trajectories.class));
        renderModules.add(Elara.moduleManager.getModule(PotionHUD.class));
        renderModules.add(Elara.moduleManager.getModule(WaterMark.class));

        List<Module> utilityModules = new ArrayList<>();
        utilityModules.add(Elara.moduleManager.getModule(AutoSwap.class));
        utilityModules.add(Elara.moduleManager.getModule(AutoTool.class));
        utilityModules.add(Elara.moduleManager.getModule(ChestStealer.class));
        utilityModules.add(Elara.moduleManager.getModule(InvManager.class));
        utilityModules.add(Elara.moduleManager.getModule(InvWalk.class));
        utilityModules.add(Elara.moduleManager.getModule(SpeedMine.class));
        utilityModules.add(Elara.moduleManager.getModule(AntiDebuff.class));
        utilityModules.add(Elara.moduleManager.getModule(InventoryClicker.class));

        List<Module> worldModules = new ArrayList<>();
        worldModules.add(Elara.moduleManager.getModule(Clutch.class));
        worldModules.add(Elara.moduleManager.getModule(BedBreaker.class));
        worldModules.add(Elara.moduleManager.getModule(BedTracker.class));
        worldModules.add(Elara.moduleManager.getModule(FastPlace.class));
        worldModules.add(Elara.moduleManager.getModule(Scaffold.class));
        worldModules.add(Elara.moduleManager.getModule(AutoBlockIn.class));
        worldModules.add(Elara.moduleManager.getModule(Telly.class));
        worldModules.add(Elara.moduleManager.getModule(BedPlates.class));

        List<Module> exploitModules = new ArrayList<>();
        exploitModules.add(Elara.moduleManager.getModule(BackTrack.class));
        exploitModules.add(Elara.moduleManager.getModule(BlinkSettings.class));
        exploitModules.add(Elara.moduleManager.getModule(FakeLag.class));
        exploitModules.add(Elara.moduleManager.getModule(GhostHand.class));
        exploitModules.add(Elara.moduleManager.getModule(elara.module.exploit.Timer.class));
        exploitModules.add(Elara.moduleManager.getModule(FastBow.class));
        exploitModules.add(Elara.moduleManager.getModule(Disabler.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(Elara.moduleManager.getModule(Spammer.class));
        miscModules.add(Elara.moduleManager.getModule(NoRotate.class));
        miscModules.add(Elara.moduleManager.getModule(NickHider.class));
        miscModules.add(Elara.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(Elara.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(Elara.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(Elara.moduleManager.getModule(ClientSpoofer.class));
        miscModules.add(Elara.moduleManager.getModule(FlagDetector.class));
        miscModules.add(Elara.moduleManager.getModule(ServerLag.class));
        miscModules.add(Elara.moduleManager.getModule(HackerDetector.class));
        miscModules.add(Elara.moduleManager.getModule(AntiBot.class));
        miscModules.add(Elara.moduleManager.getModule(MCF.class));

        Comparator<Module> comparator = Comparator.nullsLast((m1, m2) -> {
            String name1 = m1.getName();
            String name2 = m2.getName();
            if (name1 == null && name2 == null) return 0;
            if (name1 == null) return 1;
            if (name2 == null) return -1;
            return name1.toLowerCase().compareTo(name2.toLowerCase());
        });
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        utilityModules.sort(comparator);
        worldModules.sort(comparator);
        exploitModules.sort(comparator);
        miscModules.sort(comparator);

        this.categoryList = new ArrayList<>();
        int topOffset = 5;

        CategoryComponent combat = new CategoryComponent("Combat", combatModules);
        combat.setY(topOffset);
        categoryList.add(combat);
        topOffset += 20;

        CategoryComponent movement = new CategoryComponent("Movement", movementModules);
        movement.setY(topOffset);
        categoryList.add(movement);
        topOffset += 20;

        CategoryComponent render = new CategoryComponent("Render", renderModules);
        render.setY(topOffset);
        categoryList.add(render);
        topOffset += 20;

        CategoryComponent utility = new CategoryComponent("Utility", utilityModules);
        utility.setY(topOffset);
        categoryList.add(utility);
        topOffset += 20;

        CategoryComponent world = new CategoryComponent("World", worldModules);
        world.setY(topOffset);
        categoryList.add(world);
        topOffset += 20;

        CategoryComponent exploit = new CategoryComponent("Exploit", exploitModules);
        exploit.setY(topOffset);
        categoryList.add(exploit);
        topOffset += 20;

        CategoryComponent misc = new CategoryComponent("Misc", miscModules);
        misc.setY(topOffset);
        categoryList.add(misc);

        loadPositions();
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public ArrayList<CategoryComponent> getCategoryList() {
        return categoryList;
    }

    public void initGui() {
        super.initGui();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long time = System.currentTimeMillis();

        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 140).getRGB());

        mc.fontRendererObj.drawStringWithShadow("elara " + Elara.version, 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT * 2, new Color(60, 162, 253).getRGB());
        mc.fontRendererObj.drawStringWithShadow("dev, tenyze", 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT, new Color(60, 162, 253).getRGB());

        for (CategoryComponent category : categoryList) {
            category.render(this.fontRendererObj);
            category.handleDrag(mouseX, mouseY);

            for (Component module : category.getModules()) {
                module.update(mouseX, mouseY);
            }
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            if (!handleScrollBinding(scrollDir)) {
                for (CategoryComponent category : categoryList) {
                    category.onScroll(mouseX, mouseY, scrollDir);
                }
            }
        }
    }

    private boolean handleScrollBinding(int scrollDir) {
        for (CategoryComponent category : categoryList) {
            for (Component module : category.getModules()) {
                if (module instanceof ModuleComponent) {
                    ModuleComponent modComp = (ModuleComponent) module;
                    if (modComp.panelExpand) {
                        for (Component setting : modComp.getSettingsList()) {
                            if (setting instanceof BindComponent && ((BindComponent) setting).isBinding()) {
                                ((BindComponent) setting).onScroll(scrollDir);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            for (int i = categoryList.size() - 1; i >= 0; i--) {
                CategoryComponent category = categoryList.get(i);
                if (category.insideArea(mouseX, mouseY)) {
                    if (category.isHovered(mouseX, mouseY)) {
                        category.setPin(!category.isPin());
                    } else if (category.mousePressed(mouseX, mouseY)) {
                        category.setOpened(!category.isOpened());
                    } else {
                        category.mousePressed(true);
                        category.xx = mouseX - category.getX();
                        category.yy = mouseY - category.getY();
                    }
                    return;
                }
            }
        }

        for (int i = categoryList.size() - 1; i >= 0; i--) {
            CategoryComponent category = categoryList.get(i);
            if (category.isOpened() && !category.getModules().isEmpty() && category.isPointInModuleArea(mouseX, mouseY)) {
                category.mouseDown(mouseX, mouseY, mouseButton);
                return;
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            for (CategoryComponent category : categoryList) {
                category.mousePressed(false);
            }
        }

        for (int i = categoryList.size() - 1; i >= 0; i--) {
            CategoryComponent category = categoryList.get(i);
            if (category.isOpened() && !category.getModules().isEmpty() && category.isPointInModuleArea(mouseX, mouseY)) {
                category.mouseReleased(mouseX, mouseY, mouseButton);
                return;
            }
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }

        Iterator<CategoryComponent> btnCat = categoryList.iterator();
        while (true) {
            CategoryComponent cat;
            do {
                do {
                    if (!btnCat.hasNext()) {
                        return;
                    }
                    cat = btnCat.next();
                } while (!cat.isOpened());
            } while (cat.getModules().isEmpty());
            cat.keyTyped(typedChar, keyCode);
        }
    }

    public void onGuiClosed() {
        savePositions();
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
