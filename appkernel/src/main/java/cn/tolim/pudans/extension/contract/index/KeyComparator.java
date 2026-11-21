package cn.tolim.pudans.extension.contract.index;

import java.util.Comparator;
import org.jspecify.annotations.Nullable;

public class KeyComparator implements Comparator<String> {
    public static final KeyComparator INSTANCE = new KeyComparator();

    @Override
    public int compare(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) {
            return 0;
        } else if (a == null) {

            return 1;
        } else if (b == null) {

            return -1;
        }
        return compareStrings(a, b);
    }

    private int compareStrings(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char charA = a.charAt(i);
            char charB = b.charAt(j);

            if (Character.isDigit(charA) && Character.isDigit(charB)) {

                int compareResult = compareNumbers(a, b, i, j);
                if (compareResult != 0) {
                    return compareResult;
                }


                i = moveIndexToNextNonDigit(a, i);
                j = moveIndexToNextNonDigit(b, j);
            } else if (charA == charB) {

                i++;
                j++;
            } else if (Character.isDigit(charA)) {

                return -1;
            } else if (Character.isDigit(charB)) {

                return 1;
            } else {

                return Character.compare(charA, charB);
            }
        }

        return Integer.compare(a.length(), b.length());
    }

    private int compareNumbers(String a, String b, int startA, int startB) {
        int i = startA;
        int j = startB;


        int lengthA = countDigits(a, i);
        int lengthB = countDigits(b, j);
        if (lengthA != lengthB) {
            return Integer.compare(lengthA, lengthB);
        }


        for (int k = 0; k < lengthA && i < a.length() && j < b.length(); k++, i++, j++) {
            char charA = a.charAt(i);
            char charB = b.charAt(j);
            if (charA != charB) {
                return Character.compare(charA, charB);
            }
        }


        boolean hasDecimalA = i < a.length() && a.charAt(i) == '.';
        boolean hasDecimalB = j < b.length() && b.charAt(j) == '.';
        if (hasDecimalA || hasDecimalB) {
            return compareDecimalNumbers(a, b, i, j);
        }

        return 0;
    }

    private int compareDecimalNumbers(String a, String b, int startA, int startB) {

        int pointA = a.indexOf('.', startA);
        int pointB = b.indexOf('.', startB);


        int integerComparison = compareIntegerPart(a, b, startA, startB, pointA, pointB);
        if (integerComparison != 0) {
            return integerComparison;
        }


        return compareFractionalPart(a, b, pointA + 1, pointB + 1);
    }

    private int compareIntegerPart(String a, String b, int startA, int startB, int pointA, int pointB) {
        int i = startA;
        int j = startB;

        int lengthA = pointA - i;
        int lengthB = pointB - j;
        if (lengthA != lengthB) {
            return Integer.compare(lengthA, lengthB);
        }

        while (i < pointA && j < pointB) {
            char charA = a.charAt(i);
            char charB = b.charAt(j);
            if (charA != charB) {
                return Character.compare(charA, charB);
            }
            i++;
            j++;
        }

        return 0;
    }

    private int compareFractionalPart(String a, String b, int i, int j) {
        while (i < a.length() && j < b.length() && Character.isDigit(a.charAt(i)) && Character.isDigit(b.charAt(j))) {
            if (a.charAt(i) != b.charAt(j)) {
                return Character.compare(a.charAt(i), b.charAt(j));
            }
            i++;
            j++;
        }


        while (i < a.length() && Character.isDigit(a.charAt(i))) {
            if (a.charAt(i) != '0') {
                return 1;
            }
            i++;
        }
        while (j < b.length() && Character.isDigit(b.charAt(j))) {
            if (b.charAt(j) != '0') {
                return -1;
            }
            j++;
        }

        return 0;
    }

    private int countDigits(String s, int start) {
        int count = 0;
        while (start < s.length() && Character.isDigit(s.charAt(start))) {
            count++;
            start++;
        }
        return count;
    }

    private int moveIndexToNextNonDigit(String s, int index) {
        while (index < s.length() && (Character.isDigit(s.charAt(index)) || s.charAt(index) == '.')) {
            index++;
        }
        return index;
    }
}
