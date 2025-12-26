package forge.deckchooser;

import com.google.common.collect.ImmutableList;
import forge.deck.*;
import forge.game.GameFormat;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEvent;
import forge.gamemodes.quest.QuestEventChallenge;
import forge.gamemodes.quest.QuestUtil;
import forge.gui.FThreads;
import forge.gui.UiCommand;
import forge.item.PaperCard;
import forge.itemmanager.DeckManager;
import forge.itemmanager.ItemManagerConfig;
import forge.itemmanager.ItemManagerContainer;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.controllers.CDetailPicture;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("serial")
public class FDeckChooser extends JPanel implements IDecksComboBoxListener {
    // Guard to avoid starting multiple concurrent background loads
    private final AtomicBoolean loadingDecks = new AtomicBoolean(false);
    // Last time updateCustom was requested (ms)
    private volatile long lastUpdateRequestTime = 0L;
    private DecksComboBox decksComboBox;
    private DeckType selectedDeckType;
    private ItemManagerContainer lstDecksContainer;
    private NetDeckCategory netDeckCategory;
    private NetDeckArchiveStandard NetDeckArchiveStandard;
    private NetDeckArchivePioneer NetDeckArchivePioneer;
    private NetDeckArchiveModern NetDeckArchiveModern;
    private NetDeckArchivePauper NetDeckArchivePauper;
    private NetDeckArchiveLegacy NetDeckArchiveLegacy;
    private NetDeckArchiveVintage NetDeckArchiveVintage;
    private NetDeckArchiveBlock NetDeckArchiveBlock;

    private boolean refreshingDeckType;
    private boolean isForCommander;

    private final DeckManager lstDecks;
    final Localizer localizer = Localizer.getInstance();

    private final FLabel btnViewDeck = new FLabel.ButtonBuilder().text(localizer.getMessage("lblViewDeck")).fontSize(14).build();
    private final FLabel btnRandom = new FLabel.ButtonBuilder().fontSize(14).build();

    private boolean isAi;

    private final ForgePreferences prefs = FModel.getPreferences();
    private FPref stateSetting = null;

    //Show dialog to select a deck
    public static Deck promptForDeck(final CDetailPicture cDetailPicture, final String title, final DeckType defaultDeckType, final boolean forAi) {
        FThreads.assertExecutedByEdt(true);
        boolean isForCommander = defaultDeckType.equals(DeckType.COMMANDER_DECK);
        final FDeckChooser chooser = new FDeckChooser(cDetailPicture, forAi, isForCommander? GameType.Commander : GameType.Constructed, isForCommander);
        chooser.initialize(defaultDeckType);
        chooser.populate();
        final Dimension parentSize = JOptionPane.getRootFrame().getSize();
        chooser.setMinimumSize(new Dimension((int)(parentSize.getWidth() / 2), (int)parentSize.getHeight() - 200));
        final Localizer localizer = Localizer.getInstance();
        final FOptionPane optionPane = new FOptionPane(null, title, null, chooser, ImmutableList.of(localizer.getMessage("lblOK"), localizer.getMessage("lblCancel")), 0);
        optionPane.setDefaultFocus(chooser);
        chooser.lstDecks.setItemActivateCommand((UiCommand) () -> {
            //accept selected deck on double click or Enter
            optionPane.setResult(0);
        });
        optionPane.setVisible(true);
        final int dialogResult = optionPane.getResult();
        optionPane.dispose();
        if (dialogResult == 0) {
            return chooser.getDeck();
        }
        return null;
    }

    public FDeckChooser(final CDetailPicture cDetailPicture, final boolean forAi, GameType gameType, boolean forCommander) {
        lstDecks = new DeckManager(gameType, cDetailPicture);
        setOpaque(false);
        isAi = forAi;
        isForCommander = forCommander;
        final UiCommand cmdViewDeck = () -> {
            if (selectedDeckType != DeckType.COLOR_DECK && selectedDeckType != DeckType.THEME_DECK) {
                FDeckViewer.show(getDeck());
            }
        };
        lstDecks.setItemActivateCommand(cmdViewDeck);
        btnViewDeck.setCommand(cmdViewDeck);
    }

    public void initialize() {
        initialize(DeckType.COLOR_DECK);
    }
    public void initialize(final DeckType defaultDeckType) {
        initialize(null, defaultDeckType);
    }
    public void initialize(final FPref savedStateSetting, final DeckType defaultDeckType) {
        stateSetting = savedStateSetting;
        selectedDeckType = defaultDeckType;
    }

    public DeckType getSelectedDeckType() { return selectedDeckType; }
    public void setSelectedDeckType(final DeckType selectedDeckType0) {
        refreshDecksList(selectedDeckType0, false, null);
    }

    public DeckManager getLstDecks() { return lstDecks; }

    private void updateDecks(final Iterable<DeckProxy> decks, final ItemManagerConfig config) {
        long updateStart = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] updateDecks() called with config: " + config);

        long step1 = System.currentTimeMillis();
        lstDecks.setAllowMultipleSelections(false);
        System.out.println("[DECK LOADING DEBUG]   - setAllowMultipleSelections: " + (System.currentTimeMillis() - step1) + "ms");

        long step2 = System.currentTimeMillis();
        lstDecks.setPool(decks);
        System.out.println("[DECK LOADING DEBUG]   - setPool: " + (System.currentTimeMillis() - step2) + "ms");

        long step3 = System.currentTimeMillis();
        lstDecks.setup(config);
        System.out.println("[DECK LOADING DEBUG]   - setup: " + (System.currentTimeMillis() - step3) + "ms");

        long step4 = System.currentTimeMillis();
        btnRandom.setText(localizer.getMessage("lblRandomDeck"));
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));
        System.out.println("[DECK LOADING DEBUG]   - button setup: " + (System.currentTimeMillis() - step4) + "ms");

        long step5 = System.currentTimeMillis();
        lstDecks.setSelectedIndex(0);
        System.out.println("[DECK LOADING DEBUG]   - setSelectedIndex: " + (System.currentTimeMillis() - step5) + "ms");

        System.out.println("[DECK LOADING DEBUG] updateDecks() total: " + (System.currentTimeMillis() - updateStart) + "ms");
    }


    private void updateCustom() {
        final long methodEntryTime = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] ================== updateCustom() ENTRY ==================");
        System.out.println("[DECK LOADING DEBUG] Entry time: " + new java.util.Date());
        System.out.println("[DECK LOADING DEBUG] Current thread: " + Thread.currentThread().getName());
        System.out.println("[DECK LOADING DEBUG] Is EDT: " + javax.swing.SwingUtilities.isEventDispatchThread());

        // Print stack trace to see what called this method
        System.out.println("[DECK LOADING DEBUG] Call stack:");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(stack.length, 12); i++) {
            System.out.println("[DECK LOADING DEBUG]   " + stack[i]);
        }

        // Prevent duplicate background loads
        lastUpdateRequestTime = System.currentTimeMillis();
        if (!loadingDecks.compareAndSet(false, true)) {
            System.out.println("[DECK LOADING DEBUG] updateCustom() called but a load is already in progress - skipping duplicate request.");
            return;
        }

        DeckFormat deckFormat = lstDecks.getGameType().getDeckFormat();

        // DEBUG: Log start of deck loading
        final long startTime = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] ========================================");
        System.out.println("[DECK LOADING DEBUG] Starting to load decks for format: " + deckFormat);
        System.out.println("[DECK LOADING DEBUG] Time: " + new java.util.Date());
        System.out.println("[DECK LOADING DEBUG] Thread: " + Thread.currentThread().getName());
        System.out.println("[DECK LOADING DEBUG] Time since method entry: " + (startTime - methodEntryTime) + "ms");

        // Load decks asynchronously to prevent GUI freeze
        System.out.println("[DECK LOADING DEBUG] About to call FThreads.invokeInBackgroundThread()...");
        final long beforeBgInvoke = System.currentTimeMillis();

        FThreads.invokeInBackgroundThread(() -> {
            try {
                final long bgThreadStartTime = System.currentTimeMillis();
                System.out.println("[DECK LOADING DEBUG] Background thread ACTUALLY started after: " +
                    (bgThreadStartTime - startTime) + "ms (since startTime)");
                System.out.println("[DECK LOADING DEBUG] Background thread ACTUALLY started after: " +
                    (bgThreadStartTime - beforeBgInvoke) + "ms (since invokeInBackgroundThread call)");
                System.out.println("[DECK LOADING DEBUG] Background thread: " + Thread.currentThread().getName());

                final Iterable<DeckProxy> decks;
                final ItemManagerConfig config;
                final String formatName;

                switch (deckFormat) {
                case Commander:
                    formatName = "Commander";
                    System.out.println("[DECK LOADING DEBUG] Loading Commander decks...");
                    long cmdStart = System.currentTimeMillis();
                    decks = DeckProxy.getAllCommanderDecks();
                    long cmdEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] Commander decks loaded in: " + (cmdEnd - cmdStart) + "ms");
                    config = ItemManagerConfig.COMMANDER_DECKS;
                    break;
                case Oathbreaker:
                    formatName = "Oathbreaker";
                    System.out.println("[DECK LOADING DEBUG] Loading Oathbreaker decks...");
                    long oathStart = System.currentTimeMillis();
                    decks = DeckProxy.getAllOathbreakerDecks();
                    long oathEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] Oathbreaker decks loaded in: " + (oathEnd - oathStart) + "ms");
                    config = ItemManagerConfig.COMMANDER_DECKS;
                    break;
                case Brawl:
                    formatName = "Brawl";
                    System.out.println("[DECK LOADING DEBUG] Loading Brawl decks...");
                    long brawlStart = System.currentTimeMillis();
                    decks = DeckProxy.getAllBrawlDecks();
                    long brawlEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] Brawl decks loaded in: " + (brawlEnd - brawlStart) + "ms");
                    config = ItemManagerConfig.COMMANDER_DECKS;
                    break;
                case TinyLeaders:
                    formatName = "TinyLeaders";
                    System.out.println("[DECK LOADING DEBUG] Loading TinyLeaders decks...");
                    long tinyStart = System.currentTimeMillis();
                    decks = DeckProxy.getAllTinyLeadersDecks();
                    long tinyEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] TinyLeaders decks loaded in: " + (tinyEnd - tinyStart) + "ms");
                    config = ItemManagerConfig.COMMANDER_DECKS;
                    break;
                default:
                    formatName = "Constructed";
                    System.out.println("[DECK LOADING DEBUG] Loading Constructed decks...");
                    long constStart = System.currentTimeMillis();
                    decks = DeckProxy.getAllConstructedDecks();
                    long constEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] Constructed decks loaded in: " + (constEnd - constStart) + "ms");
                    config = ItemManagerConfig.CONSTRUCTED_DECKS;
                    break;
                }

                // Count decks
                int deckCount = 0;
                for (@SuppressWarnings("unused") DeckProxy deck : decks) {
                    deckCount++;
                }
                System.out.println("[DECK LOADING DEBUG] Total decks loaded: " + deckCount);

                final long beforeEdtTime = System.currentTimeMillis();
                System.out.println("[DECK LOADING DEBUG] Total loading time: " + (beforeEdtTime - bgThreadStartTime) + "ms");
                System.out.println("[DECK LOADING DEBUG] About to call FThreads.invokeInEdtLater()...");
                System.out.println("[DECK LOADING DEBUG] Current time before EDT invoke: " + new java.util.Date());

                // Update UI in EDT
                final long edtInvokeTime = System.currentTimeMillis();
                FThreads.invokeInEdtLater(() -> {
                    final long edtUpdateStart = System.currentTimeMillis();
                    final long edtDelay = edtUpdateStart - edtInvokeTime;
                    System.out.println("[DECK LOADING DEBUG] -------- EDT CALLBACK STARTED --------");
                    System.out.println("[DECK LOADING DEBUG] EDT update started at: " + new java.util.Date());
                    System.out.println("[DECK LOADING DEBUG] Time waiting in EDT queue: " + edtDelay + "ms");
                    System.out.println("[DECK LOADING DEBUG] Total time from updateCustom() start: " +
                        (edtUpdateStart - startTime) + "ms");
                    System.out.println("[DECK LOADING DEBUG] EDT thread: " + Thread.currentThread().getName());

                    if (edtDelay > 1000) {
                        System.out.println("[DECK LOADING DEBUG] WARNING: EDT was blocked for " + edtDelay + "ms!");
                        System.out.println("[DECK LOADING DEBUG] This suggests the EDT is busy with something else.");
                    }

                    updateDecks(decks, config);

                    final long edtUpdateEnd = System.currentTimeMillis();
                    System.out.println("[DECK LOADING DEBUG] EDT updateDecks() took: " +
                        (edtUpdateEnd - edtUpdateStart) + "ms");
                    System.out.println("[DECK LOADING DEBUG] Total time from start: " +
                        (edtUpdateEnd - startTime) + "ms");
                    System.out.println("[DECK LOADING DEBUG] ========================================");
                    // Reset loading flag after UI update finished
                    loadingDecks.set(false);
                });
            } catch (Exception e) {
                final long errorTime = System.currentTimeMillis();
                FThreads.invokeInEdtLater(() -> {
                    System.err.println("[DECK LOADING DEBUG] ERROR at " + (errorTime - startTime) + "ms: " + e.getMessage());
                    e.printStackTrace();
                    // Ensure loading flag is cleared on error
                    loadingDecks.set(false);
                });
            }
        });

        System.out.println("[DECK LOADING DEBUG] Main thread continuing (non-blocking)...");
    }

    private void updateColors(Predicate<PaperCard> formatFilter) {
        final long colorStart = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] -------- updateColors() ENTRY --------");
        System.out.println("[DECK LOADING DEBUG] updateColors() thread: " + Thread.currentThread().getName());
        System.out.println("[DECK LOADING DEBUG] updateColors() formatFilter: " + (formatFilter != null ? "present" : "null"));

        long step1 = System.currentTimeMillis();
        lstDecks.setAllowMultipleSelections(true);
        System.out.println("[DECK LOADING DEBUG] updateColors() - setAllowMultipleSelections: " + (System.currentTimeMillis() - step1) + "ms");

        long step2 = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] updateColors() - calling ColorDeckGenerator.getColorDecks()...");
        Iterable<DeckProxy> colorDecks = ColorDeckGenerator.getColorDecks(lstDecks, formatFilter, isAi);
        System.out.println("[DECK LOADING DEBUG] updateColors() - ColorDeckGenerator.getColorDecks(): " + (System.currentTimeMillis() - step2) + "ms");

        long step3 = System.currentTimeMillis();
        lstDecks.setPool(colorDecks);
        System.out.println("[DECK LOADING DEBUG] updateColors() - setPool: " + (System.currentTimeMillis() - step3) + "ms");

        long step4 = System.currentTimeMillis();
        lstDecks.setup(ItemManagerConfig.STRING_ONLY);
        System.out.println("[DECK LOADING DEBUG] updateColors() - setup: " + (System.currentTimeMillis() - step4) + "ms");

        long step5 = System.currentTimeMillis();
        btnRandom.setText(localizer.getMessage("lblRandomColors"));
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelectColors(lstDecks));
        System.out.println("[DECK LOADING DEBUG] updateColors() - button setup: " + (System.currentTimeMillis() - step5) + "ms");

        long step6 = System.currentTimeMillis();
        // default selection = basic two color deck
        lstDecks.setSelectedIndices(new Integer[]{0, 1});
        System.out.println("[DECK LOADING DEBUG] updateColors() - setSelectedIndices: " + (System.currentTimeMillis() - step6) + "ms");

        System.out.println("[DECK LOADING DEBUG] updateColors() TOTAL: " + (System.currentTimeMillis() - colorStart) + "ms");
    }

    private void updateMatrix(GameFormat format) {
        final long start = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] -------- updateMatrix() ENTRY --------");
        System.out.println("[DECK LOADING DEBUG] updateMatrix() format: " + format);

        lstDecks.setAllowMultipleSelections(false);

        long step1 = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] updateMatrix() - calling ArchetypeDeckGenerator.getMatrixDecks()...");
        Iterable<DeckProxy> decks = ArchetypeDeckGenerator.getMatrixDecks(format, isAi);
        System.out.println("[DECK LOADING DEBUG] updateMatrix() - getMatrixDecks(): " + (System.currentTimeMillis() - step1) + "ms");

        long step2 = System.currentTimeMillis();
        lstDecks.setPool(decks);
        System.out.println("[DECK LOADING DEBUG] updateMatrix() - setPool: " + (System.currentTimeMillis() - step2) + "ms");

        long step3 = System.currentTimeMillis();
        lstDecks.setup(ItemManagerConfig.STRING_ONLY);
        System.out.println("[DECK LOADING DEBUG] updateMatrix() - setup: " + (System.currentTimeMillis() - step3) + "ms");

        btnRandom.setText("Random");
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));

        // default selection = basic two color deck
        lstDecks.setSelectedIndices(new Integer[]{0});

        System.out.println("[DECK LOADING DEBUG] updateMatrix() TOTAL: " + (System.currentTimeMillis() - start) + "ms");
    }

    private void updateRandomCommander() {
        final long start = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] -------- updateRandomCommander() ENTRY --------");

        DeckFormat deckFormat = lstDecks.getGameType().getDeckFormat();
        if (!deckFormat.hasCommander()) {
            System.out.println("[DECK LOADING DEBUG] updateRandomCommander() - no commander format, returning");
            return;
        }

        lstDecks.setAllowMultipleSelections(false);

        long step1 = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] updateRandomCommander() - calling CommanderDeckGenerator.getCommanderDecks()...");
        Iterable<DeckProxy> decks = CommanderDeckGenerator.getCommanderDecks(deckFormat, isAi, false);
        System.out.println("[DECK LOADING DEBUG] updateRandomCommander() - getCommanderDecks(): " + (System.currentTimeMillis() - step1) + "ms");

        long step2 = System.currentTimeMillis();
        lstDecks.setPool(decks);
        System.out.println("[DECK LOADING DEBUG] updateRandomCommander() - setPool: " + (System.currentTimeMillis() - step2) + "ms");

        long step3 = System.currentTimeMillis();
        lstDecks.setup(ItemManagerConfig.STRING_ONLY);
        System.out.println("[DECK LOADING DEBUG] updateRandomCommander() - setup: " + (System.currentTimeMillis() - step3) + "ms");

        btnRandom.setText("Random");
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));

        // default selection = basic two color deck
        lstDecks.setSelectedIndices(new Integer[]{0});

        System.out.println("[DECK LOADING DEBUG] updateRandomCommander() TOTAL: " + (System.currentTimeMillis() - start) + "ms");
    }

    private void updateRandomCardGenCommander() {
        final long start = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] -------- updateRandomCardGenCommander() ENTRY --------");

        DeckFormat deckFormat = lstDecks.getGameType().getDeckFormat();
        if (!deckFormat.hasCommander()) {
            System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() - no commander format, returning");
            return;
        }

        lstDecks.setAllowMultipleSelections(false);

        long step1 = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() - calling CommanderDeckGenerator.getCommanderDecks(cardGen=true)...");
        Iterable<DeckProxy> decks = CommanderDeckGenerator.getCommanderDecks(deckFormat, isAi, true);
        System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() - getCommanderDecks(): " + (System.currentTimeMillis() - step1) + "ms");

        long step2 = System.currentTimeMillis();
        lstDecks.setPool(decks);
        System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() - setPool: " + (System.currentTimeMillis() - step2) + "ms");

        long step3 = System.currentTimeMillis();
        lstDecks.setup(ItemManagerConfig.STRING_ONLY);
        System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() - setup: " + (System.currentTimeMillis() - step3) + "ms");

        btnRandom.setText("Random");
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));

        // default selection = basic two color deck
        lstDecks.setSelectedIndices(new Integer[]{0});

        System.out.println("[DECK LOADING DEBUG] updateRandomCardGenCommander() TOTAL: " + (System.currentTimeMillis() - start) + "ms");
    }

    private void updateThemes() {
        updateDecks(DeckProxy.getAllThemeDecks(), ItemManagerConfig.STRING_ONLY);
    }

    private void updatePrecons() {
        updateDecks(DeckProxy.getAllPreconstructedDecks(QuestController.getPrecons()), ItemManagerConfig.PRECON_DECKS);
    }

    private void updateCommanderPrecons() {
        updateDecks(DeckProxy.getAllCommanderPreconDecks(), ItemManagerConfig.COMMANDER_DECKS);
    }

    private void updateQuestEvents() {
        updateDecks(DeckProxy.getAllQuestEventAndChallenges(), ItemManagerConfig.QUEST_EVENT_DECKS);
    }

    private void updateRandom() {
        updateDecks(RandomDeckGenerator.getRandomDecks(lstDecks, isAi), ItemManagerConfig.STRING_ONLY);
    }

    private void updateNetDecks() {
        if (netDeckCategory != null) {
            decksComboBox.setText(netDeckCategory.getDeckType());
        }
        updateDecks(DeckProxy.getNetDecks(netDeckCategory), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchiveStandardDecks() {
        if (NetDeckArchiveStandard != null) {
            decksComboBox.setText(NetDeckArchiveStandard.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchiveStandardDecks(NetDeckArchiveStandard), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchiveModernDecks() {
        if (NetDeckArchiveModern != null) {
            decksComboBox.setText(NetDeckArchiveModern.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchiveModernDecks(NetDeckArchiveModern), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchivePauperDecks() {
        if (NetDeckArchivePauper != null) {
            decksComboBox.setText(NetDeckArchivePauper.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchivePauperDecks(NetDeckArchivePauper), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchivePioneerDecks() {
        if (NetDeckArchivePioneer != null) {
            decksComboBox.setText(NetDeckArchivePioneer.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchivePioneerDecks(NetDeckArchivePioneer), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchiveLegacyDecks() {
        if (NetDeckArchiveLegacy != null) {
            decksComboBox.setText(NetDeckArchiveLegacy.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchiveLegacyDecks(NetDeckArchiveLegacy), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchiveVintageDecks() {
        if (NetDeckArchiveVintage != null) {
            decksComboBox.setText(NetDeckArchiveVintage.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchiveVintageDecks(NetDeckArchiveVintage), ItemManagerConfig.NET_DECKS);
    }

    private void updateNetArchiveBlockDecks() {
        if (NetDeckArchiveBlock != null) {
            decksComboBox.setText(NetDeckArchiveBlock.getDeckType());
        }
        updateDecks(DeckProxy.getNetArchiveBlockDecks(NetDeckArchiveBlock), ItemManagerConfig.NET_DECKS);
    }

    public Deck getDeck() {
        final DeckProxy proxy = lstDecks.getSelectedItem();
        if (proxy == null) {
            return null;
        }
        return proxy.getDeck();
    }

    /** Generates deck from current list selection(s). */
    public RegisteredPlayer getPlayer() {
        if (lstDecks.getSelectedIndex() < 0) { return null; }

        // Special branch for quest events
        if (selectedDeckType == DeckType.QUEST_OPPONENT_DECK) {
            final QuestEvent event = DeckgenUtil.getQuestEvent(lstDecks.getSelectedItem().getName());
            final RegisteredPlayer result = new RegisteredPlayer(event.getEventDeck());
            if (event instanceof QuestEventChallenge) {
                result.setStartingLife(((QuestEventChallenge) event).getAiLife());
            }
            result.setCardsOnBattlefield(QuestUtil.getComputerStartingCards(event));
            return result;
        }

        return new RegisteredPlayer(getDeck());
    }

    public void populate() {
        if (decksComboBox == null) { //initialize components with delayed initialization the first time this is populated
            decksComboBox = new DecksComboBox();
            lstDecksContainer = new ItemManagerContainer(lstDecks);
            decksComboBox.addListener(this);
            restoreSavedState();
        } else {
            removeAll();
        }
        this.setLayout(new MigLayout("insets 0, gap 0"));
        decksComboBox.addTo(this, "w 100%, h 30px!, gapbottom 5px, spanx 2, wrap");
        this.add(lstDecksContainer, "w 100%, growy, pushy, spanx 2, wrap");
        this.add(btnViewDeck, "w 50%-3px, h 30px!, gaptop 5px, gapright 6px");
        this.add(btnRandom, "w 50%-3px, h 30px!, gaptop 5px");
        if (isShowing()) {
            revalidate();
            repaint();
        }
    }

    public final boolean isAi() {
        return isAi;
    }

    public void setIsAi(final boolean isAiDeck) {
        isAi = isAiDeck;
    }

    @Override
    public void deckTypeSelected(final DecksComboBoxEvent ev) {
        if (ev.getDeckType() == DeckType.NET_ARCHIVE_STANDARD_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchiveStandard category = NetDeckArchiveStandard.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_STANDARD_DECK && NetDeckArchiveStandard != null) {
                            decksComboBox.setText(NetDeckArchiveStandard.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchiveStandard = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });

            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_PIONEER_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchivePioneer category = NetDeckArchivePioneer.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_PIONEER_DECK && NetDeckArchivePioneer != null) {
                            decksComboBox.setText(NetDeckArchivePioneer.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchivePioneer = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_MODERN_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchiveModern category = NetDeckArchiveModern.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_MODERN_DECK && NetDeckArchiveModern != null) {
                            decksComboBox.setText(NetDeckArchiveModern.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchiveModern = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_PAUPER_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchivePauper category = NetDeckArchivePauper.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_PAUPER_DECK && NetDeckArchivePauper != null) {
                            decksComboBox.setText(NetDeckArchivePauper.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchivePauper = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_LEGACY_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchiveLegacy category = NetDeckArchiveLegacy.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_LEGACY_DECK && NetDeckArchiveLegacy != null) {
                            decksComboBox.setText(NetDeckArchiveLegacy.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchiveLegacy = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_VINTAGE_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchiveVintage category = NetDeckArchiveVintage.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_VINTAGE_DECK && NetDeckArchiveVintage != null) {
                            decksComboBox.setText(NetDeckArchiveVintage.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchiveVintage = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if (ev.getDeckType() == DeckType.NET_ARCHIVE_BLOCK_DECK && !refreshingDeckType) {
            if (lstDecks.getGameType() != GameType.Constructed)
                return;
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckArchiveBlock category = NetDeckArchiveBlock.selectAndLoad(lstDecks.getGameType());
                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_ARCHIVE_BLOCK_DECK && NetDeckArchiveBlock != null) {
                            decksComboBox.setText(NetDeckArchiveBlock.getDeckType());
                        }
                        return;
                    }

                    NetDeckArchiveBlock = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;

        } else if ((ev.getDeckType() == DeckType.NET_DECK || ev.getDeckType() == DeckType.NET_COMMANDER_DECK) && !refreshingDeckType) {
            //needed for loading net decks
            FThreads.invokeInBackgroundThread(() -> {
                final NetDeckCategory category = NetDeckCategory.selectAndLoad(lstDecks.getGameType());

                FThreads.invokeInEdtLater(() -> {
                    if (category == null) {
                        decksComboBox.setDeckType(selectedDeckType); //restore old selection if user cancels
                        if (selectedDeckType == DeckType.NET_DECK && netDeckCategory != null) {
                            decksComboBox.setText(netDeckCategory.getDeckType());
                        }
                        return;
                    }

                    netDeckCategory = category;
                    refreshDecksList(ev.getDeckType(), true, ev);
                });
            });
            return;
        }
        refreshDecksList(ev.getDeckType(), false, ev);
    }

    public void refreshDeckListForAI() {
        //remember current deck by name, refresh decklist for AI/Human then reselect if possible
        String currentName = lstDecks.getSelectedItem().getName();

        UiCommand selectCmd = lstDecks.getSelectCommand();
        // ignore selection changes while refreshing to avoid repeating some deck generator calls
        lstDecks.setSelectCommand(null);

        refreshDecksList(selectedDeckType, true, null);

        lstDecks.setSelectedString(currentName);

        lstDecks.setSelectCommand(selectCmd);
        lstDecks.refresh();

        saveState();
    }

    private void refreshDecksList(final DeckType deckType, final boolean forceRefresh, final DecksComboBoxEvent ev) {
        final long refreshStart = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] refreshDecksList() called with deckType=" + deckType + ", forceRefresh=" + forceRefresh);
        System.out.println("[DECK LOADING DEBUG] refreshDecksList() thread: " + Thread.currentThread().getName());
        System.out.println("[DECK LOADING DEBUG] refreshDecksList() is EDT: " + javax.swing.SwingUtilities.isEventDispatchThread());

        if (decksComboBox == null) {
            System.out.println("[DECK LOADING DEBUG] refreshDecksList() - decksComboBox is null, returning early");
            return;
        }
        if (selectedDeckType == deckType && !forceRefresh) {
            System.out.println("[DECK LOADING DEBUG] refreshDecksList() - same deck type and no force refresh, returning early");
            return;
        }
        selectedDeckType = deckType;

        if (ev == null) {
            System.out.println("[DECK LOADING DEBUG] refreshDecksList() - ev is null, calling decksComboBox.refresh()...");
            final long beforeRefresh = System.currentTimeMillis();
            refreshingDeckType = true;
            decksComboBox.refresh(deckType, isForCommander);
            refreshingDeckType = false;
            System.out.println("[DECK LOADING DEBUG] refreshDecksList() - decksComboBox.refresh() took: " + (System.currentTimeMillis() - beforeRefresh) + "ms");
        }

        System.out.println("[DECK LOADING DEBUG] refreshDecksList() - calling lstDecks.setCaption()...");
        lstDecks.setCaption(deckType.toString());
        System.out.println("[DECK LOADING DEBUG] refreshDecksList() - about to switch on deckType: " + deckType);
        System.out.println("[DECK LOADING DEBUG] refreshDecksList() - time so far: " + (System.currentTimeMillis() - refreshStart) + "ms");

        switch (deckType) {
            case CUSTOM_DECK:
                updateCustom();
                break;
            case COMMANDER_DECK:
                updateCustom();
                break;
            case COLOR_DECK:
                updateColors(null);
                break;
            case STANDARD_COLOR_DECK:
                updateColors(FModel.getFormats().getStandard().getFilterPrinted());
                break;
            case MODERN_COLOR_DECK:
                updateColors(FModel.getFormats().getModern().getFilterPrinted());
                break;
            case PAUPER_COLOR_DECK:
                updateColors(FModel.getFormats().getPauper().getFilterPrinted());
                break;
            case STANDARD_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().getStandard());
                }
                break;
            case PIONEER_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().getPioneer());
                }
                break;
            case HISTORIC_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().getHistoric());
                }
                break;
            case MODERN_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().getModern());
                }
                break;
            case LEGACY_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().get("Legacy"));
                }
                break;
            case VINTAGE_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().get("Vintage"));
                }
                break;
            case PAUPER_CARDGEN_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateMatrix(FModel.getFormats().getPauper());
                }
                break;
            case RANDOM_COMMANDER_DECK:
                updateRandomCommander();
                break;
            case RANDOM_CARDGEN_COMMANDER_DECK:
                if(FModel.isdeckGenMatrixLoaded()) {
                    updateRandomCardGenCommander();
                }
                break;
            case THEME_DECK:
                updateThemes();
                break;
            case QUEST_OPPONENT_DECK:
                updateQuestEvents();
                break;
            case PRECONSTRUCTED_DECK:
                updatePrecons();
                break;
            case PRECON_COMMANDER_DECK:
                updateCommanderPrecons();
                break;
            case RANDOM_DECK:
                updateRandom();
                break;
            case NET_DECK:
                updateNetDecks();
                break;
            case NET_COMMANDER_DECK:
                updateNetDecks();
                break;
            case NET_ARCHIVE_STANDARD_DECK:
                updateNetArchiveStandardDecks();
                break;
            case NET_ARCHIVE_MODERN_DECK:
                updateNetArchiveModernDecks();
                break;
            case NET_ARCHIVE_PAUPER_DECK:
                updateNetArchivePauperDecks();
                break;
            case NET_ARCHIVE_PIONEER_DECK:
                updateNetArchivePioneerDecks();
                break;
            case NET_ARCHIVE_LEGACY_DECK:
                updateNetArchiveLegacyDecks();
                break;
            case NET_ARCHIVE_VINTAGE_DECK:
                updateNetArchiveVintageDecks();
                break;
            case NET_ARCHIVE_BLOCK_DECK:
                updateNetArchiveBlockDecks();
                break;
            default:
                break; //other deck types not currently supported here
        }
    }

    private final String SELECTED_DECK_DELIMITER = "::";

    public void saveState() {
        if (stateSetting == null) {
            throw new NullPointerException("State setting missing. Specify first using the initialize() method.");
        }
        prefs.setPref(stateSetting, getState());
        prefs.save();
    }

    private String getState() {
        final StringBuilder state = new StringBuilder();
        DeckType selectedDeckType = this.selectedDeckType;   // decksComboBox.getDeckType()
        if (selectedDeckType == DeckType.NET_ARCHIVE_STANDARD_DECK) {
            if (NetDeckArchiveStandard == null) { return ""; }
            state.append(NetDeckArchiveStandard.PREFIX).append(NetDeckArchiveStandard.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_PIONEER_DECK) {
            if (NetDeckArchivePioneer == null) { return ""; }
            state.append(NetDeckArchivePioneer.PREFIX).append(NetDeckArchivePioneer.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_MODERN_DECK) {
            if (NetDeckArchiveModern == null) { return ""; }
            state.append(NetDeckArchiveModern.PREFIX).append(NetDeckArchiveModern.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_PAUPER_DECK) {
            if (NetDeckArchivePauper == null) { return ""; }
            state.append(NetDeckArchivePauper.PREFIX).append(NetDeckArchivePauper.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_LEGACY_DECK) {
            if (NetDeckArchiveLegacy == null) { return ""; }
            state.append(NetDeckArchiveLegacy.PREFIX).append(NetDeckArchiveLegacy.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_VINTAGE_DECK) {
            if (NetDeckArchiveVintage == null) { return ""; }
            state.append(NetDeckArchiveVintage.PREFIX).append(NetDeckArchiveVintage.getName());
        } else if (selectedDeckType == DeckType.NET_ARCHIVE_BLOCK_DECK) {
            if (NetDeckArchiveBlock == null) { return ""; }
            state.append(NetDeckArchiveBlock.PREFIX).append(NetDeckArchiveBlock.getName());
        } else if (selectedDeckType == null || selectedDeckType == DeckType.NET_DECK) {
            //handle special case of net decks
            if (netDeckCategory == null) { return ""; }
            state.append(NetDeckCategory.PREFIX).append(netDeckCategory.getName());
        }
        else {
            state.append(selectedDeckType.name());
        }
        state.append(";");
        joinSelectedDecks(state, SELECTED_DECK_DELIMITER);
        return state.toString();
    }

    private void joinSelectedDecks(final StringBuilder state, final String delimiter) {
        final Iterable<DeckProxy> selectedDecks = lstDecks.getSelectedItems();
        boolean isFirst = true;
        if (selectedDecks != null) {
            for (final DeckProxy deck : selectedDecks) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    state.append(delimiter);
                }
                state.append(deck.toString());
            }
        }
    }

    public void restoreSavedState() {
        final long restoreStart = System.currentTimeMillis();
        System.out.println("[DECK LOADING DEBUG] ======= restoreSavedState() ENTRY =======");
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() thread: " + Thread.currentThread().getName());
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() is EDT: " + javax.swing.SwingUtilities.isEventDispatchThread());

        final DeckType oldDeckType = selectedDeckType;
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() oldDeckType: " + oldDeckType);

        if (stateSetting == null) {
            System.out.println("[DECK LOADING DEBUG] restoreSavedState() - stateSetting is null, refreshing deck list");
            //if can't restore saved state, just refresh deck list
            refreshDecksList(oldDeckType, true, null);
            System.out.println("[DECK LOADING DEBUG] restoreSavedState() total time: " + (System.currentTimeMillis() - restoreStart) + "ms");
            return;
        }

        final String savedState = prefs.getPref(stateSetting);
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() savedState: " + savedState);

        final long beforeGetDeckType = System.currentTimeMillis();
        DeckType deckTypeFromState = getDeckTypeFromSavedState(savedState);
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() getDeckTypeFromSavedState took: " + (System.currentTimeMillis() - beforeGetDeckType) + "ms");
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() deckTypeFromState: " + deckTypeFromState);

        final long beforeRefresh = System.currentTimeMillis();
        refreshDecksList(deckTypeFromState, true, null);
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() refreshDecksList took: " + (System.currentTimeMillis() - beforeRefresh) + "ms");

        if (!lstDecks.setSelectedStrings(getSelectedDecksFromSavedState(savedState))) {
            System.out.println("[DECK LOADING DEBUG] restoreSavedState() - couldn't select old decks, refreshing again");
            //if can't select old decks, just refresh deck list
            refreshDecksList(oldDeckType, true, null);
        }
        System.out.println("[DECK LOADING DEBUG] restoreSavedState() total time: " + (System.currentTimeMillis() - restoreStart) + "ms");
    }

    private DeckType getDeckTypeFromSavedState(final String savedState) {
        try {
            if (StringUtils.isBlank(savedState)) {
                return selectedDeckType;
            } else {
                final String deckType = savedState.split(";")[0];
                if (deckType.startsWith(NetDeckCategory.PREFIX)) {
                    netDeckCategory = NetDeckCategory.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckCategory.PREFIX.length()));
                    return DeckType.NET_DECK;
                }
                if (deckType.startsWith(NetDeckArchiveStandard.PREFIX)) {
                    NetDeckArchiveStandard = NetDeckArchiveStandard.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchiveStandard.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_STANDARD_DECK;
                }
                if (deckType.startsWith(NetDeckArchivePioneer.PREFIX)) {
                    NetDeckArchivePioneer = NetDeckArchivePioneer.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchivePioneer.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_PIONEER_DECK;
                }
                if (deckType.startsWith(NetDeckArchiveModern.PREFIX)) {
                    NetDeckArchiveModern = NetDeckArchiveModern.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchiveModern.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_MODERN_DECK;
                }
                if (deckType.startsWith(NetDeckArchivePauper.PREFIX)) {
                    NetDeckArchivePauper = NetDeckArchivePauper.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchivePauper.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_PAUPER_DECK;
                }
                if (deckType.startsWith(NetDeckArchiveLegacy.PREFIX)) {
                    NetDeckArchiveLegacy = NetDeckArchiveLegacy.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchiveLegacy.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_LEGACY_DECK;
                }
                if (deckType.startsWith(NetDeckArchiveVintage.PREFIX)) {
                    NetDeckArchiveVintage = NetDeckArchiveVintage.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchiveVintage.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_VINTAGE_DECK;
                }
                if (deckType.startsWith(NetDeckArchiveBlock.PREFIX)) {
                    NetDeckArchiveBlock = NetDeckArchiveBlock.selectAndLoad(lstDecks.getGameType(), deckType.substring(NetDeckArchiveBlock.PREFIX.length()));
                    return DeckType.NET_ARCHIVE_BLOCK_DECK;
                }
                return DeckType.valueOf(deckType);
            }
        } catch (final IllegalArgumentException ex) {
            System.err.println(ex.getMessage() + ". Using default : " + selectedDeckType);
            return selectedDeckType;
        }
    }

    private List<String> getSelectedDecksFromSavedState(final String savedState) {
        try {
            if (StringUtils.isBlank(savedState)) {
                return new ArrayList<>();
            }
            final String[] parts = savedState.split(";", -1);
            return Arrays.asList(parts[1].split(SELECTED_DECK_DELIMITER));
        } catch (final Exception ex) {
            System.err.println(ex + " [savedState=" + savedState + "]");
            return new ArrayList<>();
        }
    }

    public DecksComboBox getDecksComboBox() {
        return decksComboBox;
    }
}
