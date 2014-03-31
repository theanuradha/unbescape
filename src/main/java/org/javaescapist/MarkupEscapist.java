/*
 * =============================================================================
 * 
 *   Copyright (c) 2014, The JAVAESCAPIST team (http://www.javaescapist.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.javaescapist;

import java.io.IOException;
import java.io.Writer;

/**
 * 
 * @author Daniel Fern&aacute;ndez
 * 
 * @since 1.0
 *
 */
final class MarkupEscapist {


    static enum MarkupEscapeType {
        NAMED_REFERENCES_DEFAULT_TO_DECIMAL,
        NAMED_REFERENCES_DEFAULT_TO_HEXA,
        DECIMAL_REFERENCES,
        HEXADECIMAL_REFERENCES
    }


    /*
     * GLOSSARY
     * ------------------------
     *    NCR: Named Character Reference:       &aacute;
     *    DCR: Decimal Character Reference:     &#225;
     *    HCR: Hexadecimal Character Reference: &#xE1;
     *    Codepoint: each of the int values conforming the Unicode code space.
     *               Normally corresponding to a Java char primitive value (codepoint <= U+FFFF), but might be two
     *               chars for codepoints U+FFFF to U+10FFFF if the first char is a high surrogate (\uD800 to \uDBFF)
     *               and the second is a low surrogate (\uDC00 to \uDFFF).
     *               See: http://www.oracle.com/technetwork/articles/javase/supplementary-142654.html
     */




    /*
     * Prefixes and suffix defined for use in decimal/hexa escaping and unescaping.
     */
    private static final char REFERENCE_PREFIX = '&';
    private static final char REFERENCE_NUMERIC_PREFIX2 = '#';
    private static final char REFERENCE_HEXA_PREFIX3_UPPER = 'X';
    private static final char REFERENCE_HEXA_PREFIX3_LOWER = 'x';
    private static final char[] REFERENCE_DECIMAL_PREFIX = "&#".toCharArray();
    private static final char[] REFERENCE_HEXA_PREFIX = "&#x".toCharArray();
    private static final char REFERENCE_SUFFIX = ';';

    /*
     * Small utility char arrays for hexadecimal conversion
     */
    private static char[] HEXA_CHARS_UPPER = "0123456789ABCDEF".toCharArray();
    private static char[] HEXA_CHARS_LOWER = "0123456789abcdef".toCharArray();




    private MarkupEscapist() {
        super();
    }






    static String escape(final EscapeSymbols symbols, final String text, final int level,
                         final MarkupEscapeType markupEscapeType) {

        if (markupEscapeType == null) {
            throw new IllegalArgumentException("Argument 'markupEscapeType' cannot be null");
        }

        if (text == null) {
            return null;
        }

        final boolean useNCRs =
                (MarkupEscapeType.NAMED_REFERENCES_DEFAULT_TO_DECIMAL.equals(markupEscapeType) ||
                 MarkupEscapeType.NAMED_REFERENCES_DEFAULT_TO_HEXA.equals(markupEscapeType));
        final boolean useHexa =
                (MarkupEscapeType.NAMED_REFERENCES_DEFAULT_TO_HEXA.equals(markupEscapeType) ||
                 MarkupEscapeType.HEXADECIMAL_REFERENCES.equals(markupEscapeType));

        StringBuilder strBuilder = null;

        final int offset = 0;
        final int max = text.length();

        int readOffset = offset;

        for (int i = offset; i < max; i++) {

            final char c = text.charAt(i);


            /*
             * Shortcut: most characters will be ASCII/Alphanumeric, and we won't need to do anything at
             * all for them
             */
            if (c <= symbols.MAX_ASCII_CHAR && level < symbols.ESCAPE_LEVELS[c]) {
                continue;
            }


            /*
             * Shortcut: we might not want to escape non-ASCII chars at all either.
             */
            if (c > symbols.MAX_ASCII_CHAR && level < symbols.ESCAPE_LEVELS[symbols.MAX_ASCII_CHAR + 1]) {
                continue;
            }


            /*
             * Compute the codepoint. This will be used instead of the char for the rest of the process.
             */

            final int codepoint;
            if (c < Character.MIN_HIGH_SURROGATE) { // shortcut: U+D800 is the lower limit of high-surrogate chars.
                codepoint = (int) c;
            } else if (Character.isHighSurrogate(c) && (i + 1) < max) {
                final char c1 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c1)) {
                    codepoint = Character.toCodePoint(c, c1);
                } else {
                    codepoint = (int) c;
                }
            } else { // just a normal, single-char, high-valued codepoint.
                codepoint = (int) c;
            }


            /*
             * At this point we know for sure we will need some kind of escaping, so we
             * can increase the offset and initialize the string builder if needed, along with
             * copying to it all the contents pending up to this point.
             */

            if (strBuilder == null) {
                strBuilder = new StringBuilder(max + 20);
            }

            if (i - readOffset > 0) {
                strBuilder.append(text, readOffset, i);
            }

            if (Character.charCount(codepoint) > 1) {
                // This is to compensate that we are actually escaping two char[] positions with a single codepoint.
                i++;
            }

            readOffset = i + 1;


            /*
             * -----------------------------------------------------------------------------------------
             *
             * Peform the real escaping, attending the different combinations of NCR, DCR and HCR needs.
             *
             * -----------------------------------------------------------------------------------------
             */

            if (useNCRs) {
                // We will try to use an NCR

                if (codepoint < symbols.NCRS_BY_CODEPOINT_LEN) {
                    // codepoint < 0x2fff - all HTML4, most HTML5

                    final short ncrIndex = symbols.NCRS_BY_CODEPOINT[codepoint];
                    if (ncrIndex != symbols.NO_NCR) {
                        // There is an NCR for this codepoint!
                        strBuilder.append(symbols.SORTED_NCRS[ncrIndex]);
                        continue;
                    } // else, just let it exit the block and let decimal/hexa escaping do its job

                } else if (symbols.NCRS_BY_CODEPOINT_OVERFLOW != null) {
                    // codepoint >= 0x2fff. NCR, if exists, will live at the overflow map (if there is one).

                    final Short ncrIndex = symbols.NCRS_BY_CODEPOINT_OVERFLOW.get(Integer.valueOf(codepoint));
                    if (ncrIndex != null) {
                        strBuilder.append(symbols.SORTED_NCRS[ncrIndex.shortValue()]);
                        continue;
                    } // else, just let it exit the block and let decimal/hexa escaping do its job

                }

            }

            /*
             * No NCR-escaping was possible (or allowed), so we need decimal/hexa escaping.
             */

            if (useHexa) {
                strBuilder.append(REFERENCE_HEXA_PREFIX);
                strBuilder.append(Integer.toHexString(codepoint));
            } else {
                strBuilder.append(REFERENCE_DECIMAL_PREFIX);
                strBuilder.append(String.valueOf(codepoint));
            }
            strBuilder.append(REFERENCE_SUFFIX);

        }


        /*
         * -----------------------------------------------------------------------------------------------
         * Final cleaning: return the original String object if no escaping was actually needed. Otherwise
         *                 append the remaining unescaped text to the string builder and return.
         * -----------------------------------------------------------------------------------------------
         */

        if (strBuilder == null) {
            return text;
        }

        if (max - readOffset > 0) {
            strBuilder.append(text, readOffset, max);
        }

        return strBuilder.toString();

    }




    static void escape(final EscapeSymbols symbols,
                final char[] text, final int offset, final int len, final Writer writer,
                final int level, final MarkupEscapeType markupEscapeType)
                throws IOException {

        if (writer == null) {
            throw new IllegalArgumentException("Argument 'writer' cannot be null");
        }

        if (markupEscapeType == null) {
            throw new IllegalArgumentException("Argument 'markupEscapeType' cannot be null");
        }

        if (offset < 0 || offset > text.length) {
            throw new IllegalArgumentException(
                    "Invalid (offset, len). offset=" + offset + ", len=" + len + ", text.length=" + text.length);
        }

        if (len < 0 || (offset + len) > text.length) {
            throw new IllegalArgumentException(
                    "Invalid (offset, len). offset=" + offset + ", len=" + len + ", text.length=" + text.length);
        }

        if (text == null || text.length == 0) {
            return;

        }


        // TODO Fill with code from String version, once it is finished


    }




    /*
     * This translation is needed during unescaping to support ill-formed escaping codes for Windows 1252 codes
     * instead of the correct unicode ones (for example, &#x80; for the euro symbol instead of &#x20aC;). This is
     * something browsers do support, and included in the HTML5 spec for consuming character references.
     * See http://www.w3.org/TR/html5/syntax.html#consume-a-character-reference
     */
    static int translateIllFormedCodepoint(final int codepoint) {
        switch (codepoint) {
            case 0x00: return 0xFFFD;
            case 0x80: return 0x20AC;
            case 0x82: return 0x201A;
            case 0x83: return 0x0192;
            case 0x84: return 0x201E;
            case 0x85: return 0x2026;
            case 0x86: return 0x2020;
            case 0x87: return 0x2021;
            case 0x88: return 0x02C6;
            case 0x89: return 0x2030;
            case 0x8A: return 0x0160;
            case 0x8B: return 0x2039;
            case 0x8C: return 0x0152;
            case 0x8E: return 0x017D;
            case 0x91: return 0x2018;
            case 0x92: return 0x2019;
            case 0x93: return 0x201C;
            case 0x94: return 0x201D;
            case 0x95: return 0x2022;
            case 0x96: return 0x2013;
            case 0x97: return 0x2014;
            case 0x98: return 0x02DC;
            case 0x99: return 0x2122;
            case 0x9A: return 0x0161;
            case 0x9B: return 0x203A;
            case 0x9C: return 0x0153;
            case 0x9E: return 0x017E;
            case 0x9F: return 0x0178;
            default: return codepoint;
        }
    }


    /*
     * This method is used instead of Integer.parseInt(str,radix) in order to avoid the need
     * to create substrings of the text being unescaped to feed such method.
     * -  No need to check all chars are within the radix limits - reference parsing code will already have done so.
     */
    static int parseIntFromReference(final String text, final int start, final int end, final int radix) {
        int result = 0;
        for (int i = start; i < end; i++) {
            final char c = text.charAt(i);
            int n = -1;
            for (int j = 0; j < HEXA_CHARS_UPPER.length; j++) {
                if (c == HEXA_CHARS_UPPER[j] || c == HEXA_CHARS_LOWER[j]) {
                    n = j;
                    break;
                }
            }
            result = (radix * result) + n;
        }
        return result;
    }



    /*
     * See: http://www.w3.org/TR/html5/syntax.html#consume-a-character-reference
     */
    static String unescape(final String text) {

        if (text == null) {
            return text;
        }

        final EscapeSymbols symbols = EscapeSymbols.HTML5_SYMBOLS;
        StringBuilder strBuilder = null;

        final int offset = 0;
        final int max = text.length();

        int readOffset = offset;
        int referenceOffset = offset;

        for (int i = offset; i < max; i++) {

            final char c = text.charAt(i);

            /*
             * Check the need for an unescape operation at this point
             */

            if (c != REFERENCE_PREFIX || (i + 1) >= max) {
                continue;
            }

            int codepoint = 0;

            if (c == REFERENCE_PREFIX) {

                final char c1 = text.charAt(i + 1);

                if (c1 == '\u0020' || // SPACE
                    c1 == '\n' ||     // LF
                    c1 == '\u0009' || // TAB
                    c1 == '\u000C' || // FF
                    c1 == '\u003C' || // LES-THAN SIGN
                    c1 == '\u0026') { // AMPERSAND
                    // Not a character references. No characters are consumed, and nothing is returned.
                    continue;

                } else if (c1 == REFERENCE_NUMERIC_PREFIX2) {

                    if (i + 2 >= max) {
                        // No reference possible
                        continue;
                    }

                    final char c2 = text.charAt(i + 2);

                    if ((c2 == REFERENCE_HEXA_PREFIX3_LOWER || c2 == REFERENCE_HEXA_PREFIX3_UPPER) && (i + 3) < max) {
                        // This is a hexadecimal reference

                        int f = i + 3;
                        while (f < max) {
                            final char cf = text.charAt(f);
                            if (!((cf >= '0' && cf <= '9') || (cf >= 'A' && cf <= 'F') || (cf >= 'a' && cf <= 'f'))) {
                                break;
                            }
                            f++;
                        }

                        if ((f - (i + 3)) <= 0) {
                            // We weren't able to consume any hexa chars
                            continue;
                        }

                        codepoint = parseIntFromReference(text, i + 3, f, 16);
                        referenceOffset = f - 1;

                        if ((f < max) && text.charAt(f) == REFERENCE_SUFFIX) {
                            referenceOffset++;
                        }

                        codepoint = translateIllFormedCodepoint(codepoint);

                        // Don't continue here, just let the unescaping code below do its job

                    } else if (c2 >= '0' && c2 <= '9') {
                        // This is a decimal reference

                        int f = i + 2;
                        while (f < max) {
                            final char cf = text.charAt(f);
                            if (!(cf >= '0' && cf <= '9')) {
                                break;
                            }
                            f++;
                        }

                        if ((f - (i + 2)) <= 0) {
                            // We weren't able to consume any decimal chars
                            continue;
                        }

                        codepoint = parseIntFromReference(text, i + 2, f, 10);
                        referenceOffset = f - 1;

                        if ((f < max) && text.charAt(f) == REFERENCE_SUFFIX) {
                            referenceOffset++;
                        }

                        codepoint = translateIllFormedCodepoint(codepoint);

                        // Don't continue here, just let the unescaping code below do its job

                    } else {
                        // This is not a valid reference, just discard
                        continue;
                    }


                } else {

                    // This is a named reference, must be comprised only of ALPHABETIC chars

                    int f = i + 1;
                    while (f < max) {
                        final char cf = text.charAt(f);
                        if (!((cf >= 'a' && cf <= 'z') || (cf >= 'A' && cf <= 'Z') || (cf >= '0' && cf <= '9'))) {
                            break;
                        }
                        f++;
                    }

                    if ((f - (i + 1)) <= 0) {
                        // We weren't able to consume any alphanumeric
                        continue;
                    }

                    if ((f < max) && text.charAt(f) == REFERENCE_SUFFIX) {
                        f++;
                    }

                    final int ncrPosition = EscapeSymbols.binarySearch(symbols.SORTED_NCRS, text, i, f);
                    if (ncrPosition >= 0) {
                        codepoint = symbols.SORTED_CODEPOINTS[ncrPosition];
                    } else if (ncrPosition == Integer.MIN_VALUE) {
                        // Not found! Just ignore our efforts to find a match.
                        continue;
                    } else if (ncrPosition < -10) {
                        // Found but partial!
                        final int partialIndex = (-1) * (ncrPosition + 10);
                        final char[] partialMatch = symbols.SORTED_NCRS[partialIndex];
                        codepoint = symbols.SORTED_CODEPOINTS[partialIndex];
                        f -= ((f - i) - partialMatch.length); // un-consume the chars remaining from the partial match
                    } else {
                        // Should never happen!
                        throw new RuntimeException("Invalid unescaping codepoint after search: " + ncrPosition);
                    }

                    referenceOffset = f - 1;

                }

            }


            /*
             * At this point we know for sure we will need some kind of unescaping, so we
             * can increase the offset and initialize the string builder if needed, along with
             * copying to it all the contents pending up to this point.
             */

            if (strBuilder == null) {
                strBuilder = new StringBuilder(max + 5);
            }

            if (i - readOffset > 0) {
                strBuilder.append(text, readOffset, i);
            }

            i = referenceOffset;
            readOffset = i + 1;

            /*
             * --------------------------
             *
             * Peform the real unescaping
             *
             * --------------------------
             */

            if (codepoint > '\uFFFF') {
                strBuilder.append(Character.toChars(codepoint));
            } else if (codepoint < 0) {
                // This is a double-codepoint unescaping operation
                final int[] codepoints = symbols.DOUBLE_CODEPOINTS[((-1) * codepoint) - 1];
                if (codepoints[0] > '\uFFFF') {
                    strBuilder.append(Character.toChars(codepoints[0]));
                } else {
                    strBuilder.append((char) codepoints[0]);
                }
                if (codepoints[1] > '\uFFFF') {
                    strBuilder.append(Character.toChars(codepoints[1]));
                } else {
                    strBuilder.append((char) codepoints[1]);
                }
            } else {
                strBuilder.append((char)codepoint);
            }

        }


        /*
         * -----------------------------------------------------------------------------------------------
         * Final cleaning: return the original String object if no unescaping was actually needed. Otherwise
         *                 append the remaining escaped text to the string builder and return.
         * -----------------------------------------------------------------------------------------------
         */

        if (strBuilder == null) {
            return text;
        }

        if (max - readOffset > 0) {
            strBuilder.append(text, readOffset, max);
        }

        return strBuilder.toString();

    }



}

