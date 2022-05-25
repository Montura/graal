/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.api.strings.test.ops;

import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;
import com.oracle.truffle.api.strings.test.TStringTestUtil;

@RunWith(Parameterized.class)
public class TStringIndexOfAnyIntUTF32Test extends TStringTestBase {

    @Parameter public TruffleString.IntIndexOfAnyIntUTF32Node node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.IntIndexOfAnyIntUTF32Node> data() {
        return Arrays.asList(TruffleString.IntIndexOfAnyIntUTF32Node.create(), TruffleString.IntIndexOfAnyIntUTF32Node.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(new TruffleString.Encoding[]{TruffleString.Encoding.UTF_32}, true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            int intLength = array.length / 4;
            Assert.assertEquals(0, node.execute(a, 0, intLength, new int[]{(char) TStringTestUtil.readValue(array, 2, 0)}));
            Assert.assertEquals(0, node.execute(a, 0, intLength, new int[]{0, (char) TStringTestUtil.readValue(array, 2, 0)}));
            int lastInt = TStringTestUtil.readValue(array, 2, intLength - 1);
            Assert.assertEquals(indexOfInt(array, lastInt), node.execute(a, 0, intLength, new int[]{lastInt}));
        });
    }

    private static int indexOfInt(byte[] array, int c) {
        for (int i = 0; i < array.length / 4; i++) {
            if (TStringTestUtil.readValue(array, 2, i) == c) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void testNull() throws Exception {
        expectNullPointerException(() -> node.execute(null, 0, 1, new int[]{0}));
        expectNullPointerException(() -> node.execute(S_UTF32, 0, 1, null));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        checkOutOfBoundsFromTo(true, 2, new TruffleString.Encoding[]{TruffleString.Encoding.UTF_32}, (a, i, j, encoding) -> node.execute(a, i, j, new int[]{0}));
    }
}
