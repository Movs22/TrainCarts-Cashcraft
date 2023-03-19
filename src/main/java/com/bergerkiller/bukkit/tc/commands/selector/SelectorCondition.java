package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.utils.BoundingRange;

/**
 * The key-value condition component of a selector query. Supports Minecraft
 * selector expressions such as ranges, as well as text wildcard matching
 * as used for tag and name matching.
 */
public class SelectorCondition {
    private final String key;
    private final String value;

    protected SelectorCondition(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Gets the key to which this selector value was bound
     *
     * @return key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Gets the full value expression as provided by the user
     *
     * @return value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Checks whether any of the given String values matches this selector
     * value expression. If this is an inversion expression, it inverts the entire
     * match, so !name means that 'name' is not contained in the collection.
     *
     * @param values Text values
     * @return True if any of the values match
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesAnyText(Collection<String> values) throws SelectorException {
        return values.contains(this.value);
    }

    /**
     * Checks whether any of the String values in the stream matches this selector
     * value expression. If this is an inversion expression, it inverts the entire
     * match, so !name means that 'name' is not contained in the stream.
     *
     * @param values
     * @return True if any of the values match
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesAnyText(Stream<String> values) throws SelectorException {
        return values.anyMatch(Predicate.isEqual(this.value));
    }

    /**
     * Checks whether the given String value matches this selector
     * value expression
     *
     * @param value Text value
     * @return True if the value matches
     * @throws SelectorException If something is wrong with the expression
     */
    public boolean matchesText(String value) throws SelectorException {
        return this.value.equals(value);
    }

    /**
     * Gets the bounding range of values specified. Throws a
     * SelectorException if this value expression does not denote a number.
     *
     * @return bounding range
     * @throws SelectorException
     */
    public BoundingRange getBoundingRange() throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Gets the argument value specified as a floating point value.
     * Throws a SelectorException if this value expression does not denote
     * a number, or specifies a range of numbers instead of a single number.
     *
     * @return value
     * @throws SelectorException
     */
    public double getDouble() throws SelectorException {
        BoundingRange range = this.getBoundingRange();
        if (range.isZeroLength()) {
            return range.getMin();
        } else {
            throw new SelectorException(key + " value is a range, expected a single number");
        }
    }

    /**
     * Checks whether the given number value matches this selector
     * value expression. Throws a SelectorException if this value expression
     * does not denote a number.
     *
     * @param value Value to compare
     * @return True if the value matches the number
     */
    public boolean matchesNumber(double value) throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Checks whether the given number value matches this selector
     * value expression. Throws a SelectorException if this value expression
     * does not denote a number.
     *
     * @param value Value to compare
     * @return True if the value matches the number
     */
    public boolean matchesNumber(long value) throws SelectorException {
        throw new SelectorException(key + " value is not a number");
    }

    /**
     * Checks whether the given boolean matches this selector value
     * expression. This depends on how the selector was specified. It
     * supports number notation (condition=1 or condition=0), boolean
     * notation (condition=yes, condition=true, etc.) and no value
     * expression at all (condition or !condition).
     *
     * @param value Value to compare
     * @return True if the boolean matches
     */
    public boolean matchesBoolean(boolean value) throws SelectorException {
        throw new SelectorException(key + " value is not a boolean flag");
    }

    /**
     * Whether this selector condition represents a number or a range of
     * numbers.
     *
     * @return True if a number was specified
     */
    public boolean isNumber() {
        return false;
    }

    /**
     * Parses the value expression into its components so it can be used to
     * compare text and numbers with it.
     *
     * @param key The original key to which the value was bound
     * @param value Value to parse
     * @return Selector value representation of the input text value
     */
    public static SelectorCondition parse(String key, String value) {
        // Handle ! inversion, which only operates on the entire condition
        // This is so that expressions such as !1..5 works (checking outside of range)
        // The ! cannot be used for individual text expressions
        if (value.startsWith("!")) {
            return new SelectorConditionInverted(key, value, parse(key, value.substring(1)));
        }

        // Check for a range (multiple) of values, acts as an OR for text
        int rangeStart = value.indexOf("..");
        if (rangeStart != -1) {
            // Check for more beyond rangeStart
            int rangeCurrent = value.indexOf("..", rangeStart + 2);
            if (rangeCurrent == -1) {
                // Check for a numeric range, which also handles text if needed
                String first = (rangeStart > 0) ? value.substring(0, rangeStart) : null;
                String second = ((rangeStart + 2) < value.length()) ? value.substring(rangeStart + 2) : null;
                SelectorConditionNumeric min = (first != null)
                        ? SelectorConditionNumeric.tryParse(key, first)
                        : SelectorConditionNumeric.RANGE_MIN;
                SelectorConditionNumeric max = (second != null)
                        ? SelectorConditionNumeric.tryParse(key, second)
                        : SelectorConditionNumeric.RANGE_MAX;
                if (min != null && max != null) {
                    return new SelectorConditionNumericRange(key, value, min, max);
                }

                // List of 2 text values
                return new SelectorConditionAnyOfText(key, value, parsePart(key, first), parsePart(key, second));
            } else {
                // Collect a list of values
                List<SelectorCondition> selectorValues = new ArrayList<SelectorCondition>(5);
                if (rangeStart > 0) {
                    selectorValues.add(parsePart(key, value.substring(0, rangeStart)));
                }
                if (rangeCurrent > (rangeStart + 2)) {
                    selectorValues.add(parsePart(key, value.substring(rangeStart + 2, rangeCurrent)));
                }

                int rangeNext;
                while ((rangeNext = value.indexOf("..", rangeCurrent + 2)) != -1) {
                    if (rangeNext > (rangeCurrent + 2)) {
                        selectorValues.add(parsePart(key, value.substring(rangeCurrent, rangeNext)));
                    }
                    rangeCurrent = rangeNext;
                }
                if ((rangeCurrent + 2) < value.length()) {
                    selectorValues.add(parsePart(key, value.substring(rangeCurrent + 2)));
                }
                return new SelectorConditionAnyOfText(key, value, selectorValues.toArray(new SelectorCondition[selectorValues.size()]));
            }
        }

        // Not a range of values, standard single-value parsing
        return parsePart(key, value);
    }

    /**
     * Parses a text selector value. If wildcards are used, returns
     * a special type of selector value that can match multiple values.
     * Checks for valid numbers and returns a number-compatible condition
     * if one can be parsed.
     *
     * @param key
     * @param value
     * @return selector value
     */
    private static SelectorCondition parsePart(String key, String value) {
        // Check for numbers
        if (ParseUtil.isNumeric(value)) {
            SelectorConditionNumeric numeric = SelectorConditionNumeric.tryParse(key, value);
            if (numeric != null) {
                return numeric;
            }
        }

        // Check for text with wildcards (*)
        final String[] elements = value.split("\\*", -1);
        if (elements.length > 1) {
            boolean firstAny = value.startsWith("*");
            boolean lastAny = value.endsWith("*");
            return new SelectorConditionWildcardText(key, value, elements, firstAny, lastAny);
        }

        // Truthy value
        SelectorConditionTruthy truthy = SelectorConditionTruthy.tryParse(key, value);
        if (truthy != null) {
            return truthy;
        }

        // Normal text value, nothing special
        return new SelectorCondition(key, value);
    }

    /**
     * Tries to parse all conditions specified within a conditions string.
     * If parsing fails, returns null.
     *
     * @param conditionsString String of conditions to parse
     * @return Parsed conditions, or null if parsing failed (invalid syntax)
     */
    public static List<SelectorCondition> parseAll(String conditionsString) {
        int separator = conditionsString.indexOf(',');
        final int length = conditionsString.length();
        if (separator == -1) {
            // A single condition provided
            // Parse as a singleton list, with an expected key=value syntax
            // Reject invalid matches such as value, =value and value=
            int equals = conditionsString.indexOf('=');
            if (equals == -1 || equals == 0 || equals == (length-1)) {
                return null;
            }
            return Collections.singletonList(parse(conditionsString.substring(0, equals),
                                                   conditionsString.substring(equals+1)));
        } else {
            // Multiple conditions provided, build a hashmap with them
            List<SelectorCondition> conditions = new ArrayList<SelectorCondition>(10);
            int argStart = 0;
            int argEnd = separator;
            boolean valid = true;
            while (true) {
                int equals = conditionsString.indexOf('=', argStart);
                if (equals == -1 || equals == argStart || equals >= (argEnd-1)) {
                    valid = false;
                    break;
                }

                conditions.add(parse(conditionsString.substring(argStart, equals),
                                     conditionsString.substring(equals+1, argEnd)));

                // End of String
                if (argEnd == length) {
                    break;
                }

                // Find next separator. If none found, condition is until end of String.
                argStart = argEnd + 1;
                argEnd = conditionsString.indexOf(',', argEnd + 1);
                if (argEnd == -1) {
                    argEnd = length;
                }
            }
            if (!valid) {
                return null;
            }
            return conditions;
        }
    }

    /**
     * Inverts the remainder of the selector value
     */
    public static class SelectorConditionInverted extends SelectorCondition {
        private final SelectorCondition base;

        public SelectorConditionInverted(String key, String value, SelectorCondition base) {
            super(key, value);
            this.base = base;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            return !base.matchesAnyText(values);
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            return !base.matchesAnyText(values);
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return !base.matchesText(value);
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return base.getBoundingRange().invert();
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return !base.matchesNumber(value);
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return !base.matchesNumber(value);
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            return !base.matchesBoolean(value);
        }

        @Override
        public boolean isNumber() {
            return base.isNumber();
        }
    }

    /**
     * Selector for a singular numeric value
     */
    private static class SelectorConditionNumeric extends SelectorCondition {
        public static final SelectorConditionNumeric RANGE_MIN = new SelectorConditionNumeric("NONE", "", Double.NEGATIVE_INFINITY, Long.MIN_VALUE);
        public static final SelectorConditionNumeric RANGE_MAX = new SelectorConditionNumeric("NONE", "", Double.POSITIVE_INFINITY, Long.MAX_VALUE);
        public final double valueDouble;
        public final long valueLong;

        public SelectorConditionNumeric(String key, String value, double valueDouble, long valueLong) {
            super(key, value);
            this.valueDouble = valueDouble;
            this.valueLong = valueLong;
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return BoundingRange.create(valueDouble, valueDouble);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value == valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value == valueLong;
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            if (valueLong == 0)
                return !value;
            else if (valueLong == 1)
                return value;
            else
                throw new SelectorException(getKey() + " value must be truthy (0, 1, true, etc.)");
        }

        @Override
        public boolean isNumber() {
            return true;
        }

        public static SelectorConditionNumeric tryParse(String key, String value) {
            double valueDouble = ParseUtil.parseDouble(value, Double.NaN);
            if (!Double.isNaN(valueDouble)) {
                long valueLong = ParseUtil.parseLong(value, 0L);
                return new SelectorConditionNumeric(key, value, valueDouble, valueLong);
            }
            return null;
        }
    }

    /**
     * Selector for a range min..max of numeric values
     */
    private static class SelectorConditionNumericRange extends SelectorCondition {
        private final SelectorConditionNumeric min, max;

        public SelectorConditionNumericRange(String key, String value, SelectorConditionNumeric min, SelectorConditionNumeric max) {
            super(key, value);
            if (min.valueDouble > max.valueDouble) {
                this.min = max;
                this.max = min;
            } else {
                this.min = min;
                this.max = max;
            }
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            return min.matchesAnyText(values) || max.matchesAnyText(values);
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            Collection<String> tmp = values.collect(Collectors.toList());
            return min.matchesAnyText(tmp) || max.matchesAnyText(tmp);
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return min.matchesText(value) || max.matchesText(value);
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            return BoundingRange.create(min.valueDouble, max.valueDouble);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            return value >= min.valueDouble && value <= max.valueDouble;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            return value >= min.valueLong && value <= max.valueLong;
        }

        @Override
        public boolean isNumber() {
            return true;
        }
    }

    /**
     * Selector for a text value that includes wildcards (*)
     */
    public static class SelectorConditionWildcardText extends SelectorCondition {
        private final String[] elements;
        private final boolean firstAny;
        private final boolean lastAny;

        public SelectorConditionWildcardText(String key, String value, String[] elements, boolean firstAny, boolean lastAny) {
            super(key, value);
            this.elements = elements;
            this.firstAny = firstAny;
            this.lastAny = lastAny;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            for (String value : values) {
                if (matchesText(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            return values.anyMatch(this::matchesText);
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            return Util.matchText(value, this.elements, this.firstAny, this.lastAny);
        }
    }

    /**
     * Simple any-of for a list of selector values to check against
     */
    private static class SelectorConditionAnyOfText extends SelectorCondition {
        private final SelectorCondition[] selectorValues;

        public SelectorConditionAnyOfText(String key, String value, SelectorCondition... selectorValues) {
            super(key, value);
            this.selectorValues = selectorValues;
        }

        @Override
        public boolean matchesAnyText(Collection<String> values) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesAnyText(values)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesAnyText(Stream<String> values) throws SelectorException {
            //TODO: Could alternatively collect to a List and use matchesAnyText on that instead
            return values.anyMatch(s -> {
                for (SelectorCondition selectorValue : selectorValues) {
                    if (selectorValue.matchesText(s)) {
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        public boolean matchesText(String value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesText(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public BoundingRange getBoundingRange() throws SelectorException {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (SelectorCondition selectorValue : selectorValues) {
                double value = selectorValue.getBoundingRange().getMin(); // always 1 value
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }
            return BoundingRange.create(min, max);
        }

        @Override
        public boolean matchesNumber(double value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesNumber(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean matchesNumber(long value) throws SelectorException {
            for (SelectorCondition selectorValue : selectorValues) {
                if (selectorValue.matchesNumber(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A text expression that can indicate text, or a true/false condition
     */
    private static class SelectorConditionTruthy extends SelectorCondition {
        private static final Map<String, Boolean> truthyValues = new HashMap<>();
        static {
            register("yes", Boolean.TRUE);
            register("true", Boolean.TRUE);
            register("no", Boolean.FALSE);
            register("false", Boolean.FALSE);
        }

        private static void register(String key, Boolean value) {
            truthyValues.put(key, value); // true
            truthyValues.put(key.toLowerCase(Locale.ENGLISH), value); // TRUE
            truthyValues.put(key.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                    key.substring(1), value); // True
        }

        private final boolean truthyValue;

        protected SelectorConditionTruthy(String key, String value, boolean truthyValue) {
            super(key, value);
            this.truthyValue = truthyValue;
        }

        @Override
        public boolean matchesBoolean(boolean value) throws SelectorException {
            return value == truthyValue;
        }

        public static SelectorConditionTruthy tryParse(String key, String value) {
            Boolean truthy = truthyValues.get(value);
            return (truthy == null) ? null : new SelectorConditionTruthy(key, value, truthy.booleanValue());
        }
    }
}
