/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteObjectNode;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMPointerStoreNode extends LLVMStoreNodeCommon {

    public LLVMPointerStoreNode() {
        this(null);
    }

    public LLVMPointerStoreNode(LLVMSourceLocation sourceLocation) {
        super(sourceLocation);
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doAddress(LLVMNativePointer addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        getLLVMMemoryCached().putPointer(addr, toNative.executeWithTarget(value));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected void doOpDerefHandle(LLVMNativePointer addr, Object value) {
        doTruffleObject(getDerefHandleGetReceiverNode().execute(addr), value);
    }

    @Specialization
    protected void doAddress(LLVMVirtualAllocationAddress address, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
        address.writeI64(memory, toNative.executeWithTarget(value).asNative());
    }

    @Specialization
    protected void doBoxed(LLVMBoxedPrimitive address, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        if (address.getValue() instanceof Long) {
            getLLVMMemoryCached().putPointer((long) address.getValue(), toNative.executeWithTarget(value));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + address.getValue());
        }
    }

    @Specialization
    protected void doGlobal(LLVMGlobal address, Object value,
                    @Cached("create()") WriteObjectNode globalAccess) {
        globalAccess.execute(address, value);
    }

    @Specialization
    protected void doTruffleObject(LLVMManagedPointer address, Object value) {
        getForeignWriteNode().execute(address, value);
    }
}
