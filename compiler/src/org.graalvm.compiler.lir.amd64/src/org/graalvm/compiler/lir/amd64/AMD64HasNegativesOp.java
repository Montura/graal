/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRMVOp.SHLX;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.amd64.AMD64EncodeISOArrayOp.supports;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp",
        lineStart = 3415,
        lineEnd   = 3631,
        commit    = "7719a74cec8c47fd036226b520a5fce7887386da",
        sha1      = "bb0a5ceba73bfeeec91bd96f738a48b5275ac51e")
// @formatter:on
@Opcode("HAS_NEGATIVES")
public final class AMD64HasNegativesOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HasNegativesOp> TYPE = LIRInstructionClass.create(AMD64HasNegativesOp.class);

    @Def({REG}) private Value resultValue;
    @Alive({REG}) private Value originArrayValue;
    @Alive({REG, CONST}) private Value originLengthValue;

    @Temp({REG}) private Value arrayValue;
    @Temp({REG}) private Value lenValue;

    @Temp({REG}) private Value tmpValue1;

    @Temp({REG}) private Value vecValue1;
    @Temp({REG}) private Value vecValue2;

    @Temp({REG, ILLEGAL}) protected Value maskValue1;
    @Temp({REG, ILLEGAL}) protected Value maskValue2;

    public AMD64HasNegativesOp(LIRGeneratorTool tool, Value result, Value array, Value length) {
        super(TYPE);

        this.resultValue = result;
        this.originArrayValue = array;
        this.originLengthValue = length;

        this.arrayValue = tool.newVariable(array.getValueKind());
        this.lenValue = tool.newVariable(length.getValueKind());

        this.tmpValue1 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));

        LIRKind lirKind = LIRKind.value(supportsAVX2(tool.target()) ? AMD64Kind.V256_BYTE : AMD64Kind.V128_BYTE);
        this.vecValue1 = tool.newVariable(lirKind);
        this.vecValue2 = tool.newVariable(lirKind);

        if (supportsAVX3(tool.target())) {
            this.maskValue1 = tool.newVariable(LIRKind.value(AMD64Kind.MASK64));
            this.maskValue2 = tool.newVariable(LIRKind.value(AMD64Kind.MASK64));
        } else {
            this.maskValue1 = Value.ILLEGAL;
            this.maskValue2 = Value.ILLEGAL;
        }
    }

    private static boolean supportsAVX2(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.AVX2);
    }

    private static boolean supportsAVX3(TargetDescription target) {
        return supports(target, CPUFeature.AVX512VL);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelTrue = new Label();
        Label labelFalse = new Label();
        Label labelDone = new Label();
        Label labelCompareChar = new Label();
        Label labelCompareVectors = new Label();
        Label labelCompareByte = new Label();

        AMD64Move.move(crb, masm, arrayValue, originArrayValue);
        AMD64Move.move(crb, masm, lenValue, originLengthValue);

        Register ary1 = asRegister(arrayValue);
        Register len = asRegister(lenValue);
        Register result = asRegister(resultValue);

        Register tmp1 = asRegister(tmpValue1);
        Register vec1 = asRegister(vecValue1);
        Register vec2 = asRegister(vecValue2);

        // len == 0
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelFalse, false);

        if (supportsAVX3(crb.target) && supports(crb.target, CPUFeature.AVX512BW) && supports(crb.target, CPUFeature.BMI2)) {
            Label labelTest64Loop = new Label();
            Label labelTestTail = new Label();

            Register tmp3Aliased = len;
            Register mask1 = asRegister(maskValue1);
            Register mask2 = asRegister(maskValue2);

            masm.movl(tmp1, len);
            masm.emit(VPXOR, vec2, vec2, vec2, ZMM);
            // tail count (in chars) 0x3
            masm.andl(tmp1, 64 - 1);
            // vector count (in chars)
            masm.andlAndJcc(len, ~(64 - 1), ConditionFlag.Zero, labelTestTail, true);

            masm.leaq(ary1, new AMD64Address(ary1, len, Scale.Times1));
            masm.negq(len);

            masm.bind(labelTest64Loop);
            // Check whether our 64 elements of size byte contain negatives
            masm.evpcmpgtb(mask1, vec2, new AMD64Address(ary1, len, Scale.Times1));
            masm.kortestq(mask1, mask1);
            masm.jcc(ConditionFlag.NotZero, labelTrue);

            masm.addqAndJcc(len, 64, ConditionFlag.NotZero, labelTest64Loop, true);

            masm.bind(labelTestTail);
            // bail out when there is nothing to be done
            masm.testlAndJcc(tmp1, -1, ConditionFlag.Zero, labelFalse, false);

            // ~(~0 << len) applied up to two times (for 32-bit scenario)
            masm.movq(tmp3Aliased, 0xFFFFFFFF_FFFFFFFFL);
            masm.emit(SHLX, tmp3Aliased, tmp3Aliased, tmp1, QWORD);
            masm.notq(tmp3Aliased);

            masm.kmovq(mask2, tmp3Aliased);

            masm.evpcmpgtb(mask1, mask2, vec2, new AMD64Address(ary1, 0));
            masm.ktestq(mask1, mask2);
            masm.jcc(ConditionFlag.NotZero, labelTrue);
            masm.jmp(labelFalse);
        } else {
            masm.movl(result, len);

            if (supportsAVX2(crb.target)) {
                // With AVX2, use 32-byte vector compare
                Label labelCompareWideVectors = new Label();
                Label labelCompareTail = new Label();

                // Compare 32-byte vectors
                // tail count (in bytes)
                masm.andl(result, 0x0000001f);
                // vector count (in bytes)
                masm.andlAndJcc(len, 0xffffffe0, ConditionFlag.Zero, labelCompareTail, true);

                masm.leaq(ary1, new AMD64Address(ary1, len, Scale.Times1));
                masm.negq(len);

                masm.movl(tmp1, 0x80808080); // create mask to test for Unicode chars in vector
                masm.movdl(vec2, tmp1);
                masm.emit(VPBROADCASTD, vec2, vec2, AVXSize.YMM);

                masm.bind(labelCompareWideVectors);
                masm.vmovdqu(vec1, new AMD64Address(ary1, len, Scale.Times1));
                masm.vptest(vec1, vec2);
                masm.jccb(ConditionFlag.NotZero, labelTrue);
                masm.addqAndJcc(len, 32, ConditionFlag.NotZero, labelCompareWideVectors, false);

                masm.testlAndJcc(result, result, ConditionFlag.Zero, labelFalse, true);

                masm.vmovdqu(vec1, new AMD64Address(ary1, result, Scale.Times1, -32));
                masm.vptest(vec1, vec2);
                masm.jccb(ConditionFlag.NotZero, labelTrue);
                masm.jmpb(labelFalse);

                masm.bind(labelCompareTail); // len is zero
                masm.movl(len, result);
                // Fallthru to tail compare
            } else {
                // With SSE4.2, use double quad vector compare
                Label labelCompareWideVectors = new Label();
                Label labelCompareTail = new Label();

                // Compare 16-byte vectors
                // tail count (in bytes)
                masm.andl(result, 0x0000000f);
                // vector count (in bytes)
                masm.andlAndJcc(len, 0xfffffff0, ConditionFlag.Zero, labelCompareTail, false);

                masm.leaq(ary1, new AMD64Address(ary1, len, Scale.Times1));
                masm.negq(len);

                masm.movl(tmp1, 0x80808080);
                masm.movdl(vec2, tmp1);
                masm.pshufd(vec2, vec2, 0);

                masm.bind(labelCompareWideVectors);
                masm.movdqu(vec1, new AMD64Address(ary1, len, Scale.Times1));
                masm.ptest(vec1, vec2);
                masm.jcc(ConditionFlag.NotZero, labelTrue);
                masm.addqAndJcc(len, 16, ConditionFlag.NotZero, labelCompareWideVectors, false);

                masm.testlAndJcc(result, result, ConditionFlag.Zero, labelFalse, false);

                masm.movdqu(vec1, new AMD64Address(ary1, result, Scale.Times1, -16));
                masm.ptest(vec1, vec2);
                masm.jccb(ConditionFlag.NotZero, labelTrue);
                masm.jmpb(labelFalse);

                masm.bind(labelCompareTail); // len is zero
                masm.movl(len, result);
                // Fallthru to tail compare
            }
        }
        // Compare 4-byte vectors
        // vector count (in bytes)
        masm.andlAndJcc(len, 0xfffffffc, ConditionFlag.Zero, labelCompareChar, true);

        masm.leaq(ary1, new AMD64Address(ary1, len, Scale.Times1));
        masm.negq(len);

        masm.bind(labelCompareVectors);
        masm.movl(tmp1, new AMD64Address(ary1, len, Scale.Times1));
        masm.andlAndJcc(tmp1, 0x80808080, ConditionFlag.NotZero, labelTrue, true);
        masm.addqAndJcc(len, 4, ConditionFlag.NotZero, labelCompareVectors, false);

        // Compare trailing char (final 2 bytes), if any
        masm.bind(labelCompareChar);
        masm.testlAndJcc(result, 0x2, ConditionFlag.Zero, labelCompareByte, true); // tail char
        masm.movzwl(tmp1, new AMD64Address(ary1, 0));
        masm.andlAndJcc(tmp1, 0x00008080, ConditionFlag.NotZero, labelTrue, true);
        masm.subq(result, 2);
        masm.leaq(ary1, new AMD64Address(ary1, 2));

        masm.bind(labelCompareByte);
        masm.testlAndJcc(result, 0x1, ConditionFlag.Zero, labelFalse, true); // tail byte
        masm.movzbl(tmp1, new AMD64Address(ary1, 0));
        masm.andlAndJcc(tmp1, 0x00000080, ConditionFlag.NotEqual, labelTrue, true);
        masm.jmpb(labelFalse);

        masm.bind(labelTrue);
        masm.movl(result, 1); // return true
        masm.jmpb(labelDone);

        masm.bind(labelFalse);
        masm.xorl(result, result); // return false

        // That's it
        masm.bind(labelDone);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }
}
