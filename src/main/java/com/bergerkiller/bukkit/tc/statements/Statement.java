package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Statement {
    private static List<Statement> statements = new ArrayList<>();

    public static String[] parseArray(String text) {
        return text.split(";", -1);
    }

    public static void init() {
        // Note: For same priority(), evaluated from bottom to top
        //       Late-registered statements evaluate before early ones.
        register(new StatementDestination());
        register(new StatementBoolean());
        register(new StatementRandom());
        register(new StatementProperty());
        register(new StatementName());
        register(new StatementEmpty());
        register(new StatementPassenger());
        register(new StatementOwners());
        register(new StatementTrainItems());
        register(new StatementFuel());
        register(new StatementType());
        register(new StatementVelocity());
        register(new StatementPlayerItems());
        register(new StatementPlayerHand());
        register(new StatementMob());
        register(new StatementRedstone());
        register(new StatementPermission());
        register(new StatementDirection());
        register(new StatementTag());
    }

    public static void deinit() {
        statements.clear();
    }

    public static <T extends Statement> T register(T statement) {
        int index = Collections.binarySearch(statements, statement,
                (a, b) -> Integer.compare(b.priority(), a.priority()));
        if (index < 0) index = ~index;

        // Make sure that if priority is the same, an item is inserted at the beginning
        // This allows third parties to register new statements without specifying priority,
        // overriding existing statements that start with the same name.
        {
            int itemPriority = statement.priority();
            while (index > 0 && statements.get(index - 1).priority() == itemPriority) {
                index--;
            }
        }

        statements.add(index, statement);
        return statement;
    }

    public static boolean has(MinecartMember<?> member, String text, SignActionEvent event) {
        return has(member, null, text, event);
    }

    public static boolean has(MinecartGroup group, String text, SignActionEvent event) {
        return has(null, group, text, event);
    }

    /**
     * Gets if the member or group has the statement specified.
     * If both member and group are null, then only statements that require no train
     * will function. Statements that do will return false.
     *
     * @param member to use, or null to use group
     * @param group  to use, or null to use member
     * @param text   to evaluate
     * @param event  to parse
     * @return True if successful, False if not
     */
    public static boolean has(MinecartMember<?> member, MinecartGroup group, String text, SignActionEvent event) {
        return Matcher.of(text).withMember(member).withGroup(group).withSignEvent(event).match();
    }

    public static boolean hasMultiple(MinecartMember<?> member, Iterable<String> statementTexts, SignActionEvent event) {
        return hasMultiple(member, null, statementTexts, event);
    }

    public static boolean hasMultiple(MinecartGroup group, Iterable<String> statementTexts, SignActionEvent event) {
        return hasMultiple(null, group, statementTexts, event);
    }

    /**
     * Gets if the member or group has multiple statements as specified.
     * If both member and group are null, then only statements that require no train
     * will function. Statements that do will return false.<br>
     * <br>
     * Empty statements are ignored. Statements preceeding with & use AND-logic
     * with all the statements prior, and statements preceeding with | use OR-logic.
     * Others default to AND.
     *
     * @param member to use, or null to use group
     * @param group  to use, or null to use member
     * @param text   to evaluate
     * @param event  to parse
     * @return True if successful, False if not
     */
    public static boolean hasMultiple(MinecartMember<?> member, MinecartGroup group, Iterable<String> statementTexts, SignActionEvent event) {
        boolean match = true;
        for (String statementText : statementTexts) {
            if (!statementText.isEmpty()) {
                boolean isLogicAnd = true;
                if (statementText.startsWith("&")) {
                    isLogicAnd = true;
                    statementText = statementText.substring(1);
                } else if (statementText.startsWith("|")) {
                    isLogicAnd = false;
                    statementText = statementText.substring(1);
                }
                boolean result = Statement.has(member, group, statementText, event);
                if (isLogicAnd) {
                    match &= result;
                } else {
                    match |= result;
                }
            }
        }
        return match;
    }

    /**
     * Checks if this statement matches the given text
     * The given text is lower cased.
     *
     * @param text to use
     * @return if it matches and can handle it
     */
    public abstract boolean match(String text);

    /**
     * Checks if this statement matches the given text
     * The given text is lower cased.
     * The text is the pre-text of '@'
     *
     * @param text to use
     * @return if it matches and can handle an array
     */
    public abstract boolean matchArray(String text);

    /**
     * Whether a MinecartMember or MinecartGroup is required for this statement to operate.
     * Some statements also work without using the train, and can therefore return false here.
     * If this method returns false, then <i>handle</i> can be called with null as group/member argument.<br>
     * <br>
     * <b>Default: true</b>
     * 
     * @return True if a train is required
     */
    public boolean requiresTrain() {
        return true;
    }

    /**
     * Whether a SignActionEvent is required for this statement to operate.
     * Some statements also work without using the event, and can therefore return false here.
     * If this method returns false, then <i>handle</i> can be called with null as event argument.<br>
     * <br>
     * <b>Default: false</b>
     * 
     * @return True if an event is required
     */
    public boolean requiredEvent() {
        return false;
    }

    /**
     * Defines the priority of this statement. Use this to have statements
     * match before or after other statements. Default is 0. A negative value
     * will make it match last, a positive value will have it match first.
     * 
     * @return priority
     */
    public int priority() {
        return 0;
    }

    public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
        for (MinecartMember<?> member : group) {
            if (this.handle(member, text, event)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleArray(MinecartGroup group, String[] text, SignActionEvent event) {
        for (MinecartMember<?> member : group) {
            if (this.handleArray(member, text, event)) {
                return true;
            }
        }
        return false;
    }

    public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
        return false;
    }

    public boolean handleArray(MinecartMember<?> member, String[] text, SignActionEvent event) {
        return false;
    }

    /**
     * Matches input text to find the statement and evaluate it against a group, member and/or
     * with sign context information.
     */
    public static class Matcher {
        private final String text;
        private MinecartGroup group;
        private MinecartMember<?> member;
        private SignActionEvent signEvent;
        private Statement lastStatement;
        private boolean lastStatementIsArray;

        private Matcher(String text) {
            this.text = text;
        }

        public static Matcher of(String text) {
            return new Matcher(text);
        }

        public Matcher withGroup(MinecartGroup group) {
            this.group = group;
            return this;
        }

        public Matcher withMember(MinecartMember<?> member) {
            this.member = member;
            return this;
        }

        public Matcher withSignEvent(SignActionEvent event) {
            this.signEvent = event;
            return this;
        }

        /**
         * Gets the Statement matched using the last {@link #match()} call<br>
         * <br>
         * Note that this will include statements that have failed to parse fully
         * because they require more information (sign event, group, member), but would
         * have parsed if that information existed.
         *
         * @return Statement that was matched
         */
        public Statement lastStatement() {
            return this.lastStatement;
        }

        /**
         * Gets whether the {@link #lastStatement()} matched was matched against
         * the array syntax.<br>
         * <br>
         * Note that this will include statements that have failed to parse fully
         * because they require more information (sign event, group, member), but would
         * have parsed if that information existed.
         *
         * @return True if an array statement syntax was matched
         */
        public boolean lastStatementIsArray() {
            return this.lastStatementIsArray;
        }

        /**
         * Gets whether the last {@link #match()} result actually matched a statement,
         * or that a fallback result was produced using the Tag fallback.<br>
         * <br>
         * Note that this will include statements that have failed to parse fully
         * because they require more information (sign event, group, member), but would
         * have parsed if that information existed.
         *
         * @return True if a result was actually matched. For tag statements this requires
         *         use of t@.
         */
        public boolean lastResultWasExactMatch() {
            return this.lastStatement != null &&
                    (!(this.lastStatement instanceof StatementTag) || this.lastStatementIsArray);
        }

        /**
         * Matches the input text against a compatible statement and returns
         * the result of whether the condition is True.
         *
         * @return Match result
         */
        public boolean match() {
            // Reset
            this.lastStatement = null;
            this.lastStatementIsArray = false;

            boolean inv = false;
            String text = TCConfig.statementShortcuts.replace(this.text);
            while (!text.isEmpty() && text.charAt(0) == '!') {
                text = text.substring(1);
                inv = !inv;
            }
            if (text.isEmpty()) {
                return inv;
            }
            String lowerText = text.toLowerCase();
            int idx = lowerText.indexOf('@');
            String arrayText = idx == -1 ? null : lowerText.substring(0, idx);
            String[] array = idx == -1 ? null : parseArray(text.substring(idx + 1));
            for (Statement statement : statements) {
                if (arrayText != null && statement.matchArray(arrayText)) {
                    this.lastStatement = statement;
                    this.lastStatementIsArray = true;
                    if (signEvent == null && statement.requiredEvent()) {
                        continue;
                    }
                    if (member != null) {
                        return statement.handleArray(member, array, signEvent) != inv;
                    } else if (group != null) {
                        return statement.handleArray(group, array, signEvent) != inv;
                    } else if (!statement.requiresTrain()) {
                        return statement.handleArray((MinecartMember<?>) null, array, signEvent) != inv;
                    }
                } else if (statement.match(lowerText)) {
                    this.lastStatement = statement;
                    this.lastStatementIsArray = true;
                    if (signEvent == null && statement.requiredEvent()) {
                        continue;
                    }
                    if (member != null) {
                        return statement.handle(member, text, signEvent) != inv;
                    } else if (group != null) {
                        return statement.handle(group, text, signEvent) != inv;
                    } else if (!statement.requiresTrain()) {
                        return statement.handle((MinecartMember<?>) null, text, signEvent) != inv;
                    }
                }
            }
            return inv;
        }
    }
}
