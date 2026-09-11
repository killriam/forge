package forge.screens.home;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.JMenu;

import forge.Singletons;
import forge.game.ReplayLogParser;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.gui.framework.ICDoc;
import forge.gui.util.SOptionPane;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.model.FModel;
import forge.screens.gamelearning.CGameLearningUI;
import forge.screens.home.sanctioned.VSubmenuConstructed;
import forge.toolbox.FAbsolutePositioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles Swing components of exit submenu option singleton.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 *
 */
public enum CHomeUI implements ICDoc, IMenuProvider {
    /** */
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CHomeUI.class);

    EDocID currentDocID;
    Object previousDoc = null;

    private LblMenuItem lblSelected = new LblMenuItem(VSubmenuConstructed.SINGLETON_INSTANCE);

    /** Programatically selects a menu item.
     *  @param id0 {@link forge.gui.framework.EDocID} */
    public void itemClick(final EDocID id0) {
        final ForgePreferences prefs = FModel.getPreferences();

        if (lblSelected != null) {
            lblSelected.setSelected(false);
            lblSelected.repaintSelf();
        }

        currentDocID = id0;

        if (previousDoc != null) {
            if (!previousDoc.equals(id0.getDoc().getLayoutControl())) {
                MenuUtil.setMenuProvider(null);
            }
        }

        FAbsolutePositioner.SINGLETON_INSTANCE.hideAll();
        id0.getDoc().populate();
        id0.getDoc().getLayoutControl().update();
        lblSelected = VHomeUI.SINGLETON_INSTANCE.getAllSubmenuLabels().get(id0);
        if (lblSelected != null) {
            lblSelected.setSelected(true);
        }

        prefs.setPref(FPref.SUBMENU_CURRENTMENU, id0.toString());
        prefs.save();

        previousDoc = id0.getDoc().getLayoutControl();
    }

    public EDocID getCurrentDocID() {
        return currentDocID;
    }

    /** @param lbl0 {@link forge.screens.home.LblMenuItem} */
    public void setLblSelected(final LblMenuItem lbl0) {
        this.lblSelected = lbl0;
    }

    /** @return {@link javax.swing.JLabel} */
    public LblMenuItem getLblSelected() {
        return lblSelected;
    }

    @Override
    public void register() {
    }

    /* (non-Javadoc)
     * @see forge.view.home.ICDoc#intialize()
     */
    @Override
    public void initialize() {
        Singletons.getControl().getForgeMenu().setProvider(this);

        selectPrevious();
        checkForCrashRecovery();
    }

    /* (non-Javadoc)
     * @see forge.view.home.ICDoc#update()
     */
    @Override
    public void update() {
    }

    /**
     * Pulls previous menu selection from preferences
     * and clicks it programatically.
     */
    private void selectPrevious() {
        EDocID selected = null;
        try {
            selected = EDocID.valueOf(FModel.getPreferences().getPref(FPref.SUBMENU_CURRENTMENU));
        } catch (final Exception e) { }

        if (selected != null && VHomeUI.SINGLETON_INSTANCE.getAllSubmenuLabels().get(selected) != null) {
            itemClick(selected);
        }
        else {
            itemClick(EDocID.HOME_CONSTRUCTED);
        }
    }

    /**
     * Checks {@code ForgeConstants.AUTOSAVE_DIR} for a leftover per-turn autosave — left behind
     * only when a game ended without going through the normal end-of-game save (a crash, forced
     * quit, etc.), since a normal game end deletes its own autosave (see
     * {@code ReplayNotationExporter.clearAutosave()}). Offers to open it in the Game Learning
     * Viewer, where "Replay from here" reconstructs the board at the last saved turn and
     * continues the game live - not the exact crashed game (future draws differ) but the last
     * known life totals/hands/battlefield instead of starting over.
     */
    private void checkForCrashRecovery() {
        File dir = new File(ForgeConstants.AUTOSAVE_DIR);
        File[] files = dir.exists() ? dir.listFiles((d, name) -> name.endsWith(".json")) : null;
        if (files == null || files.length == 0) return;

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        File autosaveFile = files[0];

        ReplayLogParser parser = new ReplayLogParser(autosaveFile);
        if (!parser.parse()) {
            LOG.warn("Discarding unreadable crash-recovery autosave: {}", autosaveFile);
            autosaveFile.delete();
            return;
        }

        String turnInfo = parser.getTurns() != null ? ("around turn " + parser.getTurns()) : "early in the game";
        List<String> options = Arrays.asList("Resume Game", "Discard", "Not Now");
        int choice = SOptionPane.showOptionDialog(
                "Forge appears to have closed unexpectedly during a game (" + turnInfo + ").\n"
                        + "Resume it in the Game Learning Viewer, where you can replay from the last saved turn?",
                "Resume Interrupted Game", SOptionPane.QUESTION_ICON, options);

        if (choice == 0) {
            CGameLearningUI.setPendingParser(parser);
            Singletons.getControl().setCurrentScreen(FScreen.GAME_LEARNING_SCREEN);
        } else if (choice == 1) {
            autosaveFile.delete();
        }
        // choice == 2 ("Not Now") or dialog dismissed: leave the file - prompts again next launch
    }

    /* (non-Javadoc)
     * @see forge.gui.menubar.IMenuProvider#getMenus()
     */
    @Override
    public List<JMenu> getMenus() {
        // No specific menus associated with Home screen.
        return null;
    }
}
