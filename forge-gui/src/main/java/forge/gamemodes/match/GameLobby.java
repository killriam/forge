package forge.gamemodes.match;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import forge.LobbyPlayer;
import forge.ai.AIOption;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.deck.mulligan.HandQualityTarget;
import forge.game.GameRules;
import forge.deck.FormatChoices;
import forge.game.GameFormat;
import forge.game.GameType;
import forge.game.GameView;
import forge.game.IHasGameType;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.ScenarioInfo;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.net.NetworkEventView;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.gui.util.SOptionPane;
import forge.interfaces.IGameController;
import forge.interfaces.IUpdateable;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.Localizer;
import forge.util.NameGenerator;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

public abstract class GameLobby implements IHasGameType {
    private static final Logger LOG = LoggerFactory.getLogger(GameLobby.class);
    private final static int MAX_PLAYERS = 8;
    private static final char SETUP_SEPARATOR = '|';
    private static final String SETUP_VARIANTS = "variants:";
    private static final String SETUP_FORMAT = "format:";

    private GameLobbyData data = new GameLobbyData();
    private GameType currentGameType = GameType.Constructed;
    private int lastArchenemy = 0;

    private IUpdateable listener;

    private HostedMatch hostedMatch;
    private final HashMap<LobbySlot, IGameController> gameControllers = Maps.newHashMap();

    public boolean isAllowNetworking() {
        return true;
    }

    public final boolean isMatchActive() {
        return hostedMatch != null && hostedMatch.isMatchOver() == false;
    }

    public HostedMatch getHostedMatch() {
        return hostedMatch;
    }

    public void setListener(final IUpdateable listener) {
        this.listener = listener;
    }

    public GameLobbyData getData() {
        return data;
    }
    public void setData(final GameLobbyData data) {
        this.data = data;
        updateView(true);
    }

    public GameType getGameType() {
        return currentGameType;
    }
    public void setGameType(final GameType type) {
        currentGameType = type;
    }

    public boolean hasAnyVariant() {
        return !data.appliedVariants.isEmpty();
    }

    public boolean hasVariant(final GameType variant) {
        return data.appliedVariants.contains(variant);
    }

    /** The format every deck must be legal in, or null when the lobby sets none. */
    public GameFormat getFormat() {
        final String name = data.getFormatName();
        return name == null ? null : FModel.getFormats().get(name);
    }
    private void setFormat(final GameFormat format) {
        final String name = format == null || format == GameFormat.NoFormat ? null : format.getName();
        if (!Objects.equals(name, data.getFormatName())) {
            data.setFormatName(name);
            updateView(true);
        }
    }

    /** The Format dropdown entry the applied variants and format select: a GameType or a GameFormat. */
    public Object getFormatChoice() {
        for (final GameType type : FormatChoices.FORMAT_GAME_TYPES) {
            if (hasVariant(type)) {
                return type;
            }
        }
        final GameFormat format = getFormat();
        return format == null ? GameType.Constructed : format;
    }

    public void setFormatChoice(final Object choice) {
        if (choice instanceof GameType type && type != GameType.Constructed) {
            setFormat(null);
            applyVariant(type);
            return;
        }
        for (final GameType type : FormatChoices.FORMAT_GAME_TYPES) {
            removeVariant(type);
        }
        setFormat(choice instanceof GameFormat format ? format : null);
    }

    /**
     * Saves the applied variants and format for {@link #restoreSetup()} to apply in a later lobby.
     * Does nothing in a joined lobby, whose screens copy the host's setup into their controls.
     */
    /** The {@link FPref#UI_LOBBY_SETUP} value that makes {@link #restoreSetup()} apply exactly these variants. */
    public static String variantsSetup(final Iterable<GameType> variantTypes) {
        final StringJoiner variants = new StringJoiner(",");
        for (final GameType variant : variantTypes) {
            variants.add(variant.name());
        }
        return SETUP_VARIANTS + variants;
    }

    public void saveSetup() {
        if (!hasControl()) {
            return;
        }
        final StringJoiner variants = new StringJoiner(",");
        for (final GameType variant : data.appliedVariants) {
            variants.add(variant.name());
        }
        String setup = SETUP_VARIANTS + variants;
        final String format = data.getFormatName();
        if (format != null) {
            try {
                // Custom format names may hold '=', which the preferences file splits each line on
                setup += SETUP_SEPARATOR + SETUP_FORMAT + URLEncoder.encode(format, "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        FModel.getPreferences().setPref(FPref.UI_LOBBY_SETUP, setup);
        FModel.getPreferences().save();
    }

    /**
     * Applies the variants and format that {@link #saveSetup()} saved. Any the lobby no longer offers are skipped,
     * for example an archived format once archived formats are turned off.
     */
    public void restoreSetup() {
        final List<Object> choices = FormatChoices.get(true);
        for (final String field : StringUtils.split(FModel.getPreferences().getPref(FPref.UI_LOBBY_SETUP), SETUP_SEPARATOR)) {
            if (field.startsWith(SETUP_VARIANTS)) {
                for (final String name : StringUtils.split(field.substring(SETUP_VARIANTS.length()), ',')) {
                    final GameType variant = EnumUtils.getEnum(GameType.class, name);
                    if (variant != null && (choices.contains(variant) || FormatChoices.VARIANTS.contains(variant))) {
                        applyVariant(variant);
                    }
                }
            } else if (field.startsWith(SETUP_FORMAT)) {
                final GameFormat format;
                try {
                    format = FModel.getFormats().get(URLDecoder.decode(field.substring(SETUP_FORMAT.length()), "UTF-8"));
                } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
                    // A hand-edited value can hold a broken escape
                    continue;
                }
                if (choices.contains(format)
                        || choices.contains(FormatChoices.ARCHIVED) && FormatChoices.getArchivedChoices().contains(format)) {
                    setFormat(format);
                }
            }
        }
    }

    public boolean hasAutoGeneratedVariant() {
        for (final GameType variant : data.appliedVariants) {
            if (variant.isAutoGenerated()) {
                return true;
            }
        }
        return false;
    }

    public int getNumberOfSlots() {
        return data.slots.size();
    }
    public LobbySlot getSlot(final int index) {
        if (index < 0 || index >= getNumberOfSlots()) {
            return null;
        }
        return data.slots.get(index);
    }

    /** First non-OPEN slot that isn't ready, or null if every filled slot is ready. */
    public LobbySlot findFirstUnreadySlot() {
        for (int i = 0; i < getNumberOfSlots(); i++) {
            LobbySlot slot = getSlot(i);
            if (slot == null || slot.getType() == LobbySlotType.OPEN) continue;
            if (!slot.isReady()) return slot;
        }
        return null;
    }
    public void applyToSlot(final int index, final UpdateLobbyPlayerEvent event) {
        final LobbySlot slot = getSlot(index);
        if (slot == null || event == null) {
            throw new NullPointerException();
        }

        final int nSlots = getNumberOfSlots();
        final boolean triesToChangeArchenemy = event.getArchenemy() != null;
        final boolean archenemyRemoved = triesToChangeArchenemy && !event.getArchenemy();
        final boolean hasArchenemyChanged = triesToChangeArchenemy && slot.isArchenemy() != event.getArchenemy();

        final boolean changed = slot.apply(event) || hasArchenemyChanged;

        // Change archenemy teams
        if (hasVariant(GameType.Archenemy) && hasArchenemyChanged) {
            final int newArchenemy = archenemyRemoved ? lastArchenemy : index;
            if (archenemyRemoved) {
                lastArchenemy = index;
            }
            for (int otherIndex = 0; otherIndex < nSlots; otherIndex++) {
                final LobbySlot otherSlot = getSlot(otherIndex);
                final boolean becomesArchenemy = otherIndex == newArchenemy;
                if (!archenemyRemoved && otherSlot.isArchenemy() && !becomesArchenemy) {
                    lastArchenemy = otherIndex;
                }
                otherSlot.setIsArchenemy(becomesArchenemy);
            }
        }
        applyVariantTeams();

        if (event.getType() != null) {
            //refresh decklist for slot
            listener.update(index, event.getType());
        }

        if (changed) {
            updateView(false);
        }
    }

    public IGameController getController(final int index) {
        return gameControllers.get(getSlot(index));
    }
    public GameView getGameView() {
        return hostedMatch.getGameView();
    }

    public abstract boolean hasControl();
    public abstract boolean mayEdit(int index);
    public abstract boolean mayControl(int index);
    public abstract boolean mayRemove(int index);
    protected abstract IGuiGame getGui(int index);
    protected abstract void onGameStarted();

    /** Largest number of slots this lobby may hold. Subclasses may narrow it. */
    public int getSlotLimit() {
        return MAX_PLAYERS;
    }
    public void addSlot() {
        final int newIndex = getNumberOfSlots();
        final LobbySlotType type = isAllowNetworking() ? LobbySlotType.OPEN : LobbySlotType.AI;
        addSlot(new LobbySlot(type, null, newIndex, newIndex, newIndex, false, !isAllowNetworking(), Collections.emptySet()));
    }
    protected final void addSlot(final LobbySlot slot) {
        if (slot == null) {
            throw new NullPointerException();
        }
        if (data.slots.size() >= getSlotLimit()) {
            return;
        }

        data.slots.add(slot);
        if (StringUtils.isEmpty(slot.getName()) && slot.getType() != LobbySlotType.OPEN) {
            slot.setName(randomName());
        }
        if (data.slots.size() == 1) {
            // If first slot, make archenemy
            slot.setIsArchenemy(true);
            lastArchenemy = 0;
        }
        applyVariantTeams();
        updateView(true);
    }
    private String randomName() {
        final List<String> names = Lists.newArrayListWithCapacity(MAX_PLAYERS);
        for (final LobbySlot slot : data.slots) {
            names.add(slot.getName());
        }
        return NameGenerator.getRandomName("Any", "Any", names);
    }
    protected final static String localName() {
        return FModel.getPreferences().getPref(FPref.PLAYER_NAME);
    }
    protected final static int[] localAvatarIndices() {
        return localIndices(FPref.UI_AVATARS);
    }
    protected final static int[] localSleeveIndices() {
        return localIndices(FPref.UI_SLEEVES);
    }
    /** Falls back to the seat's own index, as {@link #addSlot()} does, where a stored value is missing or invalid. */
    private static int[] localIndices(final FPref pref) {
        final String[] stored = FModel.getPreferences().getPref(pref).split(",");
        final int[] result = new int[stored.length];
        for (int i = 0; i < stored.length; i++) {
            final Integer val = Ints.tryParse(stored[i]);
            result[i] = val == null || val < 0 ? i : val;
        }
        return result;
    }

    public void removeSlot(final int index) {
        if (index < 0 || index >= data.slots.size()) {
            return;
        }

        if (getSlot(index).isArchenemy()) {
            getSlot(lastArchenemy).setIsArchenemy(true);
            // Should actually be a stack here, but that's rather involved for
            // such a nonimportant feature
            lastArchenemy = 0;
        } else if (lastArchenemy == index) {
            lastArchenemy = 0;
        } else if (index < lastArchenemy) {
            lastArchenemy--;
        }
        data.slots.remove(index);
        applyVariantTeams();
        updateView(false);
    }

    public void applyVariant(final GameType variant) {
        setGameType(variant);
        data.appliedVariants.add(variant);

        //ensure other necessary variants are unchecked
        switch (variant) {
        case Archenemy:
            data.appliedVariants.remove(GameType.ArchenemyRumble);
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            applyVariantTeams();
            break;
        case ArchenemyRumble:
            data.appliedVariants.remove(GameType.Archenemy);
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            break;
        case TwoHeadedGiant:
            // the Commander variants and Vanguard set their own life totals, and Planechase has its own team rules (CR 901.12)
            data.appliedVariants.remove(GameType.Vanguard);
            data.appliedVariants.remove(GameType.Planechase);
            data.appliedVariants.remove(GameType.Archenemy);
            data.appliedVariants.remove(GameType.ArchenemyRumble);
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.Brawl);
            applyVariantTeams();
            break;
        case Commander:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.Brawl);
            data.appliedVariants.remove(GameType.MomirBasic);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case Oathbreaker:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.Brawl);
            data.appliedVariants.remove(GameType.MomirBasic);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case TinyLeaders:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.Brawl);
            data.appliedVariants.remove(GameType.MomirBasic);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case Brawl:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.MomirBasic);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case Planechase:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            break;
        case Vanguard:
            data.appliedVariants.remove(GameType.TwoHeadedGiant);
            data.appliedVariants.remove(GameType.MomirBasic);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case MomirBasic:
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.Brawl);
            data.appliedVariants.remove(GameType.Vanguard);
            data.appliedVariants.remove(GameType.MoJhoSto);
            break;
        case MoJhoSto:
            data.appliedVariants.remove(GameType.Commander);
            data.appliedVariants.remove(GameType.Oathbreaker);
            data.appliedVariants.remove(GameType.TinyLeaders);
            data.appliedVariants.remove(GameType.Brawl);
            data.appliedVariants.remove(GameType.Vanguard);
            data.appliedVariants.remove(GameType.MomirBasic);
            break;
        default:
            break;
        }
        updateView(true);
    }

    public void removeVariant(final GameType variant) {
        final boolean changed = data.appliedVariants.remove(variant);
        if (!changed) {
            return;
        }

        if (variant == GameType.TwoHeadedGiant || variant == GameType.Archenemy) {
            for (int i = 0; i < data.slots.size(); i++) {
                data.slots.get(i).setTeam(i);
            }
        }

        if (variant == currentGameType) {
            if (hasVariant(GameType.Commander)) {
                currentGameType = GameType.Commander;
            } else if (hasVariant(GameType.Oathbreaker)) {
                currentGameType = GameType.Oathbreaker;
            } else if (hasVariant(GameType.TinyLeaders)) {
                currentGameType = GameType.TinyLeaders;
            } else if (hasVariant(GameType.Brawl)) {
                currentGameType = GameType.Brawl;
            } else {
                currentGameType = GameType.Constructed;
            }
        }
        updateView(true);
    }

    public void clearVariants() {
        data.appliedVariants.clear();
    }

    public Iterable<GameType> getAppliedVariants() {
        return data.appliedVariants;
    }

    private boolean isEnoughTeams() {
        int lastTeam = -1;
        for (final LobbySlot slot : data.slots) {
            final int team = slot.getTeam();
            if (lastTeam == -1) {
                lastTeam = team;
            } else if (lastTeam != team) {
                return true;
            }
        }
        return false;
    }

    /** Under Archenemy a seat's team follows its archenemy flag. Under Two-Headed Giant seats pair up in order, so teammates sit together (CR 805.1). */
    private void applyVariantTeams() {
        if (hasVariant(GameType.TwoHeadedGiant)) {
            for (int i = 0; i < data.slots.size(); i++) {
                data.slots.get(i).setTeam(i / 2);
            }
            return;
        }
        if (!hasVariant(GameType.Archenemy)) {
            return;
        }
        for (final LobbySlot slot : data.slots) {
            slot.setTeam(slot.isArchenemy() ? 0 : 1);
        }
    }

    protected void updateView(final boolean fullUpdate) {
        if (listener != null) {
            listener.update(fullUpdate);
        }
    }

    /** Formats one "name: problem" line, indenting any card list the problem appends. */
    private static String legalityProblemEntry(final String name, final String problem) {
        return name + ": " + problem.replace("\n", "\n    ");
    }

    /** Lists every illegal deck in one warning and offers to ignore it. Returns true if the user chose to continue anyway. */
    private static boolean confirmIgnoreDeckLegality(final List<String> problems) {
        final Localizer localizer = Localizer.getInstance();
        final StringBuilder message = new StringBuilder(localizer.getMessage("lblDecksNotLegal"));
        message.append('\n');
        for (final String problem : problems) {
            message.append('\n').append(problem);
        }
        return SOptionPane.showConfirmDialog(message.toString(), localizer.getMessage("lblInvalidDeck"),
                localizer.getMessage("lblIgnore"), localizer.getMessage("lblCancel"), false);
    }

    /** Returns a runnable to start a match with the applied variants if allowed. */
    public Runnable startGame() {
        final List<LobbySlot> activeSlots = Lists.newArrayListWithCapacity(getNumberOfSlots());
        for (final LobbySlot slot : data.slots) {
            if (slot.getType() != LobbySlotType.OPEN) {
                activeSlots.add(slot);
            }
        }

        if (activeSlots.size() < 2) {
            SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblRequiredLeastTwoPlayerStartGame"));
            return null;
        }

        if (hasVariant(GameType.TwoHeadedGiant) && (activeSlots.size() != 4 || data.slots.size() != 4)) {
            SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblTwoHeadedGiantNeedsFourPlayers"));
            return null;
        }

        if (!isEnoughTeams()) {
            SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblNotEnoughTeams"));
            return null;
        }

        final Set<GameType> variantTypes = data.appliedVariants;

        GameType autoGenerateVariant = null;
        boolean isCommanderMatch = false;
        boolean isOathbreakerMatch = false;
        boolean isTinyLeadersMatch = false;
        boolean isBrawlMatch = false;
        if (!variantTypes.isEmpty()) {
            isOathbreakerMatch = variantTypes.contains(GameType.Oathbreaker);
            isTinyLeadersMatch = variantTypes.contains(GameType.TinyLeaders);
            isBrawlMatch = variantTypes.contains(GameType.Brawl);
            isCommanderMatch = isBrawlMatch || isTinyLeadersMatch || isOathbreakerMatch || variantTypes.contains(GameType.Commander);
            if (!isCommanderMatch) {
                for (final GameType variant : variantTypes) {
                    if (variant.isAutoGenerated()) {
                        autoGenerateVariant = variant;
                        break;
                    }
                }
            }
        }

        for (final LobbySlot slot : activeSlots) {
            if (!slot.isReady()) {
                SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblPlayerIsNotReady", slot.getName()));
                return null;
            }
            if (slot.getDeck() == null && autoGenerateVariant == null) {
                SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblPleaseSpecifyPlayerDeck", slot.getName()));
                return null;
            }
        }

        final boolean checkLegality = FModel.getPreferences().getPrefBoolean(FPref.ENFORCE_DECK_LEGALITY);
        final List<String> legalityProblems = new ArrayList<>();

        //Auto-generated decks don't need to be checked here
        //Commander deck replaces regular deck and is checked later
        if (checkLegality && autoGenerateVariant == null && !isCommanderMatch) {
            final DeckFormat deckFormat = data.isLimitedMode() ? DeckFormat.Limited : GameType.Constructed.getDeckFormat();
            for (final LobbySlot slot : activeSlots) {
                final String name = slot.getName();
                final String errMsg = deckFormat.getDeckConformanceProblem(slot.getDeck());
                if (null != errMsg) {
                    legalityProblems.add(legalityProblemEntry(name, errMsg));
                }
            }
        }

        final GameFormat format = getFormat();
        if (checkLegality && format != null && autoGenerateVariant == null && !data.isLimitedMode()) {
            for (final LobbySlot slot : activeSlots) {
                final String errMsg = format.getDeckConformanceProblem(slot.getDeck());
                if (errMsg != null) {
                    legalityProblems.add(legalityProblemEntry(slot.getName(),
                            Localizer.getInstance().getMessage("lblNotLegalInFormat", format.getName(), errMsg)));
                }
            }
        }

        final List<RegisteredPlayer> players = new ArrayList<>();
        final Map<RegisteredPlayer, IGuiGame> guis = Maps.newHashMap();
        final Map<RegisteredPlayer, LobbySlot> playerToSlot = Maps.newHashMap();
        boolean hasNameBeenSet = false;
        for (final LobbySlot slot : activeSlots) {
            final IGuiGame gui = getGui(data.slots.indexOf(slot));
            final String name = slot.getName();
            final int avatar = slot.getAvatarIndex();
            final int sleeve = slot.getSleeveIndex();
            final boolean isArchenemy = slot.isArchenemy();
            final int team = slot.getTeam();
            final Set<AIOption> aiOptions = slot.getAiOptions(); // TODO: could AiOptions carry the choice of which AI is selected to play against?

            final boolean isAI = slot.getType() == LobbySlotType.AI;
            final LobbyPlayer lobbyPlayer;
            if (isAI) {
                String aiProfileOverride = slot.getAiProfile();
                lobbyPlayer = GamePlayerUtil.createAiPlayer(name, avatar, sleeve, aiOptions, aiProfileOverride);
            } else {
                boolean setNameNow = false;
                if (!hasNameBeenSet && slot.getType() == LobbySlotType.LOCAL) {
                    setNameNow = true;
                    hasNameBeenSet = true;
                }
                lobbyPlayer = GamePlayerUtil.getGuiPlayer(name, avatar, sleeve, setNameNow);
            }
            final Deck slotDeck = slot.getDeck();
            lobbyPlayer.setSleeveArtKey(slotDeck == null ? "" : slotDeck.getSleeveArtKey());
            lobbyPlayer.setSleeveArtOffset(slotDeck == null ? Deck.DEFAULT_SLEEVE_OFFSET : slotDeck.getSleeveArtOffset());

            Deck deck = slot.getDeck();
            if (autoGenerateVariant != null) {
                deck = autoGenerateVariant.autoGenerateDeck(null);
                // The generated deck replaces the seat's cards, not the planes and schemes it brought
                if (slotDeck != null) {
                    for (final DeckSection section : List.of(DeckSection.Planes, DeckSection.Schemes)) {
                        if (slotDeck.has(section)) {
                            deck.putSection(section, slotDeck.get(section));
                        }
                    }
                }
            }

            RegisteredPlayer rp;
            if (variantTypes.isEmpty()) {
                rp = new RegisteredPlayer(deck);
            } else {
                if (isCommanderMatch) {
                    final GameType commanderGameType =
                            isOathbreakerMatch ? GameType.Oathbreaker :
                                isTinyLeadersMatch ? GameType.TinyLeaders :
                                    isBrawlMatch ? GameType.Brawl :
                                        GameType.Commander;
                    if (checkLegality) {
                        final String errMsg = commanderGameType.getDeckFormat().getDeckConformanceProblem(deck);
                        if (errMsg != null) {
                            legalityProblems.add(legalityProblemEntry(name, errMsg));
                        }
                    }
                }

                final CardPool avatarPool = deck.get(DeckSection.Avatar);

                Iterable<PaperCard> schemes = null;
                Iterable<PaperCard> planes = null;

                if (variantTypes.contains(GameType.ArchenemyRumble)
                        || (variantTypes.contains(GameType.Archenemy) && isArchenemy)) {
                    final CardPool schemePool = deck.get(DeckSection.Schemes);
                    if (checkLegality) {
                        final String errMsg = DeckFormat.getSchemeSectionConformanceProblem(schemePool);
                        if (null != errMsg) {
                            legalityProblems.add(legalityProblemEntry(name, errMsg));
                        }
                    }
                    schemes = schemePool == null ? Collections.emptyList() : schemePool.toFlatList();
                }

                if (variantTypes.contains(GameType.Planechase)) {
                    final CardPool planePool = deck.get(DeckSection.Planes);
                    if (checkLegality) {
                        final String errMsg = DeckFormat.getPlaneSectionConformanceProblem(planePool);
                        if (null != errMsg) {
                            legalityProblems.add(legalityProblemEntry(name, errMsg));
                        }
                    }
                    planes = planePool == null ? Collections.emptyList() : planePool.toFlatList();
                }

                if (variantTypes.contains(GameType.Vanguard)) {
                    if (avatarPool == null || avatarPool.countAll() == 0) { //ERROR! null if avatar deselected on list
                        SOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblNoSelectedVanguardAvatarForPlayer", name));
                        return null;
                    }
                }

                rp = RegisteredPlayer.forVariants(activeSlots.size(), variantTypes, deck, schemes, isArchenemy, planes, avatarPool);
            }

            rp.setGuaranteeCardsUnderTestTop10(slot.isGuaranteeCardsUnderTestTop10());
            rp.setGuaranteedLandDrop(slot.isGuaranteedLandDrop() && slot.getType() == LobbySlotType.AI);
            rp.setStartingHandQualityTarget(HandQualityTarget.fromString(slot.getStartingHandQualityTarget()));
            rp.setTeamNumber(team);
            players.add(rp.setPlayer(lobbyPlayer));

            if (!isAI) {
                guis.put(rp, gui);
            }
            playerToSlot.put(rp, slot);
        }

        if (!legalityProblems.isEmpty() && !confirmIgnoreDeckLegality(legalityProblems)) {
            return null;
        }

        //if above checks succeed, return runnable that can be used to finish starting game
        final GameType baseGameType = data.isLimitedMode() ? GameType.Draft : GameType.Constructed;
        return () -> {
            hostedMatch = GuiBase.getInterface().hostMatch();
            hostedMatch.setOnMatchOver(this::onMatchOver);

            final GameRules scenarioRules = buildScenarioGameRules(baseGameType, players, playerToSlot, activeSlots);
            if (scenarioRules != null) {
                hostedMatch.startMatch(scenarioRules, variantTypes, players, guis, null);
            } else {
                hostedMatch.startMatch(baseGameType, variantTypes, players, guis);
            }

            for (final Player p : hostedMatch.getGame().getPlayers()) {
                final LobbySlot slot = playerToSlot.get(p.getRegisteredPlayer());
                if (p.getController() instanceof IGameController controller) {
                    gameControllers.put(slot, controller);
                }
            }

            hostedMatch.gameControllers = gameControllers;

            onGameStarted();
        };
    }

    /**
     * Builds a {@link GameRules} with any attached scenario data merged in (see
     * {@code docs/SCENARIO_STARTING_HAND_FORMAT.md}, "Von einem Deck referenzieren"), or returns
     * {@code null} if no active slot has a scenario attached — callers should fall back to the
     * plain {@code startMatch(GameType, ...)} convenience overload in that case, leaving every
     * non-scenario match's behavior byte-for-byte unchanged.
     *
     * <p>Two conventions this deliberately does NOT get wrong (both caught during design review,
     * not obvious from a first read of {@link ScenarioLibrarySetup}):</p>
     * <ul>
     *   <li>{@code scenarioStartingHands}/{@code scenarioFirstDraws} are keyed <b>positionally</b>
     *       ("P1" = {@code game.getPlayers().get(0)}), not by lobby name — and
     *       {@link HostedMatch#startMatch} always sorts players human-first before creating the
     *       {@code Game}, so the "P1"/"P2" assignment here must be computed against that same
     *       sorted order via {@link HostedMatch#sortPlayersHumanFirst}, not {@code activeSlots}'
     *       raw lobby order.</li>
     *   <li>{@code forcedPlaySequence} <i>is</i> keyed by real lobby name (no sort needed there),
     *       which is exactly {@code slot.getName()} — confirmed against how this same method
     *       builds each seat's {@code LobbyPlayer} a few lines above (both the human and AI
     *       branches construct it directly from {@code slot.getName()}).</li>
     * </ul>
     */
    private GameRules buildScenarioGameRules(final GameType baseGameType,
                                              final List<RegisteredPlayer> players,
                                              final Map<RegisteredPlayer, LobbySlot> playerToSlot,
                                              final List<LobbySlot> activeSlots) {
        boolean anyScenarioAttached = false;
        for (final LobbySlot slot : activeSlots) {
            final String sf = slot.getScenarioFileName();
            if (sf != null && !sf.isEmpty()) {
                anyScenarioAttached = true;
                break;
            }
        }
        if (!anyScenarioAttached) {
            return null;
        }

        final GameRules rules = HostedMatch.getDefaultRules(baseGameType);

        // scenarioStartingHands/scenarioFirstDraws/scenarioSkipMulligan: positional "P1"/"P2"
        // keys, matching the exact order HostedMatch.startMatch will sort players into.
        final List<RegisteredPlayer> sortedForScenario = HostedMatch.sortPlayersHumanFirst(players);
        final Map<String, List<String>> mergedStartingHands = new LinkedHashMap<>();
        final Map<String, List<String>> mergedFirstDraws = new LinkedHashMap<>();
        boolean anySkipMulligan = false;
        for (int i = 0; i < sortedForScenario.size(); i++) {
            final LobbySlot slot = playerToSlot.get(sortedForScenario.get(i));
            final ScenarioInfo si = resolveSlotScenario(slot);
            if (si == null) continue;
            final String posKey = "P" + (i + 1);
            final List<String> hand = si.playerStartingHands.get("P1");
            final List<String> draws = si.playerFirstDraws.get("P1");
            if (hand != null && !hand.isEmpty()) mergedStartingHands.put(posKey, hand);
            if (draws != null && !draws.isEmpty()) mergedFirstDraws.put(posKey, draws);
            if ("opening_hand_test".equals(si.type)) anySkipMulligan = true;
        }
        if (!mergedStartingHands.isEmpty()) {
            rules.setScenarioStartingHands(mergedStartingHands);
            rules.setScenarioFirstDraws(mergedFirstDraws);
        }
        if (anySkipMulligan) {
            rules.setScenarioSkipMulligan(true);
        }

        // forcedPlaySequence: keyed by real lobby name - slot.getName() is exactly what became
        // each seat's LobbyPlayer name above, no sort/translation needed.
        final Map<String, List<String>> mergedForcedSeq = new LinkedHashMap<>();
        // Recorded sacrifice-cost targets, index-aligned per lobby name with mergedForcedSeq -
        // lets AiController force its own sacrifice choice to match the original recording.
        final Map<String, List<String>> mergedForcedSac = new LinkedHashMap<>();
        for (final LobbySlot slot : activeSlots) {
            final ScenarioInfo si = resolveSlotScenario(slot);
            if (si == null || !si.hasForcedPlaySequence()) continue;
            final List<String> seq = si.playerForcedSequence.get("P1");
            if (seq != null && !seq.isEmpty()) {
                mergedForcedSeq.put(slot.getName(), new ArrayList<>(seq));
                final List<String> sac = si.playerForcedSacrifice.get("P1");
                if (sac != null && !sac.isEmpty()) {
                    mergedForcedSac.put(slot.getName(), new ArrayList<>(sac));
                }
            }
        }
        if (!mergedForcedSeq.isEmpty()) {
            rules.setForcedPlaySequence(mergedForcedSeq);
            if (!mergedForcedSac.isEmpty()) {
                rules.setForcedPlaySequenceSacrifice(mergedForcedSac);
            }
        }

        return rules;
    }

    /** Resolves a slot's attached scenario, if any, warning (not failing) on unused P2+ data. */
    private ScenarioInfo resolveSlotScenario(final LobbySlot slot) {
        if (slot == null) return null;
        final String sf = slot.getScenarioFileName();
        if (sf == null || sf.isEmpty()) return null;
        final ReplayLogParser parser = ReplayLogParser.resolveScenarioByIdOrFilename(sf);
        if (parser == null) {
            LOG.warn("Scenario '{}' attached to seat '{}' could not be resolved to a scenario file - ignoring", sf, slot.getName());
            return null;
        }
        final ScenarioInfo si = parser.getScenarioInfo();
        if (si == null) return null;
        for (final String pid : si.playerStartingHands.keySet()) {
            if (!"P1".equals(pid) && !si.playerStartingHands.get(pid).isEmpty()) {
                LOG.warn("Scenario '{}' attached to seat '{}' has non-empty data for '{}' - only its own 'P1' entry is ever applied, the rest is ignored", sf, slot.getName(), pid);
            }
        }
        return si;
    }

    protected void onMatchOver() {
        hostedMatch = null;
        gameControllers.clear();
        updateView(true);
    }

    public final static class GameLobbyData implements Serializable {
        private static final long serialVersionUID = 9184758307999646864L;

        private final Set<GameType> appliedVariants = EnumSet.noneOf(GameType.class);
        private final List<LobbySlot> slots = Lists.newArrayList();
        private NetworkEventView eventView;
        private boolean limitedMode;
        private String activeEventId;
        private boolean activeConformance;
        private int maximumCommanderBracket = 5; // mirrors DECKGEN_MAXIMUM_COMMANDER_BRACKET default (off)
        private String formatName;

        @Override
        public String toString() {
            final long ready = slots.stream().filter(LobbySlot::isReady).count();
            return slots.size() + " slots, " + ready + " ready" + (appliedVariants.isEmpty() ? "" : ", " + appliedVariants);
        }

        public GameLobbyData() {
        }

        public NetworkEventView getEventView() {
            return eventView;
        }
        public void setEventView(final NetworkEventView view) {
            this.eventView = view;
        }
        public boolean isLimitedMode() {
            return limitedMode;
        }
        public void setLimitedMode(final boolean limited) {
            this.limitedMode = limited;
        }
        public String getActiveEventId() {
            return activeEventId;
        }
        public void setActiveEventId(final String id) {
            this.activeEventId = id;
        }
        public boolean isActiveConformance() {
            return activeConformance;
        }
        public void setActiveConformance(final boolean conformance) {
            this.activeConformance = conformance;
        }
        public int getMaximumCommanderBracket() {
            return maximumCommanderBracket;
        }
        public void setMaximumCommanderBracket(final int bracket) {
            this.maximumCommanderBracket = bracket;
        }
        public String getFormatName() {
            return formatName;
        }
        public void setFormatName(final String name) {
            this.formatName = name;
        }
    }
}
