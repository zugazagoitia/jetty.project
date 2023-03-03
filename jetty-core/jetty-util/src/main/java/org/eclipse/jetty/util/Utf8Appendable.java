//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utf8 Appendable abstract base class
 *
 * This abstract class wraps a standard {@link java.lang.Appendable} and provides methods to append UTF-8 encoded bytes, that are converted into characters.
 *
 * This class is stateful and up to 4 calls to {@link #append(byte)} may be needed before state a character is appended to the string buffer.
 *
 * The UTF-8 decoding is done by this class and no additional buffers or Readers are used. The UTF-8 code was inspired by
 * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
 *
 * License information for Bjoern Hoehrmann's code:
 *
 * Copyright (c) 2008-2009 Bjoern Hoehrmann &lt;bjoern@hoehrmann.de&gt;
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 **/
public abstract class Utf8Appendable implements CharsetStringBuilder
{
    protected static final Logger LOG = LoggerFactory.getLogger(Utf8Appendable.class);
    // @checkstyle-disable-check : AvoidEscapedUnicodeCharactersCheck
    public static final char REPLACEMENT = '\ufffd';
    // @checkstyle-enable-check : AvoidEscapedUnicodeCharactersCheck
    public static final byte[] REPLACEMENT_UTF8 = new byte[]{(byte)0xEF, (byte)0xBF, (byte)0xBD};
    private static final int UTF8_ACCEPT = 0;
    private static final int UTF8_REJECT = 12;

    protected final Appendable _appendable;
    protected int _state = UTF8_ACCEPT;

    private static final byte[] BYTE_TABLE =
    {
        // The first part of the table maps bytes to character classes that
        // to reduce the size of the transition table and create bitmasks.
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8
    };

    private static final byte[] TRANS_TABLE =
    {
        // The second part is a transition table that maps a combination
        // of a state of the automaton and a character class to a state.
        0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
        12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
        12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
        12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
        12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12
    };

    private int _codep;
    private boolean _throwOnInvalid = true;

    public Utf8Appendable(Appendable appendable)
    {
        _appendable = appendable;
    }

    public abstract int length();

    public boolean isThrowOnInvalid()
    {
        return _throwOnInvalid;
    }

    public void setThrowOnInvalid(boolean throwOnInvalid)
    {
        _throwOnInvalid = throwOnInvalid;
    }

    protected void reset()
    {
        _state = UTF8_ACCEPT;
    }

    private void checkCharAppend() throws IOException
    {
        if (_state != UTF8_ACCEPT)
        {
            _appendable.append(REPLACEMENT);
            int state = _state;
            _state = UTF8_ACCEPT;
            if (_throwOnInvalid)
                throw new NotUtf8Exception("char appended in state " + state);
        }
    }

    public void append(char c)
    {
        try
        {
            checkCharAppend();
            _appendable.append(c);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void append(String s)
    {
        try
        {
            checkCharAppend();
            _appendable.append(s);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void append(String s, int offset, int length)
    {
        try
        {
            checkCharAppend();
            _appendable.append(s, offset, offset + length);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void append(byte b)
    {
        try
        {
            appendByte(b);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void append(ByteBuffer buf)
    {
        try
        {
            while (buf.remaining() > 0)
            {
                appendByte(buf.get());
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void append(byte[] b)
    {
        append(b, 0, b.length);
    }

    @Override
    public void append(byte[] b, int offset, int length)
    {
        try
        {
            int end = offset + length;
            for (int i = offset; i < end; i++)
            {
                appendByte(b[i]);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean append(byte[] b, int offset, int length, int maxChars)
    {
        try
        {
            int end = offset + length;
            for (int i = offset; i < end; i++)
            {
                if (length() > maxChars)
                    return false;
                appendByte(b[i]);
            }
            return true;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void appendByte(byte b) throws IOException
    {
        if (b > 0 && _state == UTF8_ACCEPT)
        {
            _appendable.append((char)(b & 0xFF));
        }
        else
        {
            int i = b & 0xFF;
            int type = BYTE_TABLE[i];
            _codep = _state == UTF8_ACCEPT ? (0xFF >> type) & i : (i & 0x3F) | (_codep << 6);
            int next = TRANS_TABLE[_state + type];

            switch (next)
            {
                case UTF8_ACCEPT:
                    _state = next;
                    if (_codep < Character.MIN_HIGH_SURROGATE)
                    {
                        _appendable.append((char)_codep);
                    }
                    else
                    {
                        for (char c : Character.toChars(_codep))
                        {
                            _appendable.append(c);
                        }
                    }
                    break;

                case UTF8_REJECT:
                    final String reason = "byte " + TypeUtil.toHexString(b) + " in state " + (_state / 12);
                    _codep = 0;
                    _state = UTF8_ACCEPT;
                    _appendable.append(REPLACEMENT);
                    if (_throwOnInvalid)
                        throw new NotUtf8Exception(reason);
                    break;

                default:
                    _state = next;
            }
        }
    }

    public boolean isUtf8SequenceComplete()
    {
        return _state == UTF8_ACCEPT;
    }

    @SuppressWarnings("serial")
    public static class NotUtf8Exception extends IllegalArgumentException
    {
        public NotUtf8Exception(String reason)
        {
            super("Not valid UTF8! " + reason);
        }
    }

    public void checkState()
    {
        if (!isUtf8SequenceComplete())
        {
            _codep = 0;
            _state = UTF8_ACCEPT;
            try
            {
                _appendable.append(REPLACEMENT);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            if (_throwOnInvalid)
                throw new NotUtf8Exception("incomplete UTF8 sequence");
        }
    }

    /**
     * @return The UTF8 so far decoded, ignoring partial code points
     */
    public abstract String getPartialString();

    /**
     * Take the partial string an reset in internal buffer, but retain
     * partial code points.
     *
     * @return The UTF8 so far decoded, ignoring partial code points
     */
    public String takePartialString()
    {
        String partial = getPartialString();
        int save = _state;
        reset();
        _state = save;
        return partial;
    }

    public String toReplacedString()
    {
        if (!isUtf8SequenceComplete())
        {
            _codep = 0;
            _state = UTF8_ACCEPT;
            try
            {
                _appendable.append(REPLACEMENT);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            Throwable th = new NotUtf8Exception("incomplete UTF8 sequence");
            if (LOG.isDebugEnabled())
                LOG.warn("Unable to get replacement string", th);
            else
                LOG.warn("Unable to get replacement string {}", th.toString());

        }
        return _appendable.toString();
    }
}
