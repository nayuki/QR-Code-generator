/* BitSet.cs -- A vector of bits.
    Copyright (C) 1998, 1999, 2000, 2001, 2004, 2005  Free Software Foundation, Inc.
 
 This file is part of GNU Classpath.
 
 GNU Classpath is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2, or (at your option)
 any later version.
 
 GNU Classpath is distributed in the hope that it will be useful, but
 WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with GNU Classpath; see the file COPYING.  If not, write to the
 Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 02110-1301 USA.
 
 Linking this library statically or dynamically with other modules is
 making a combined work based on this library.  Thus, the terms and
 conditions of the GNU General Public License cover the whole
 combination.
 
 As a special exception, the copyright holders of this library give you
 permission to link this library with independent modules to produce an
 executable, regardless of the license terms of these independent
 modules, and to copy and distribute the resulting executable under
 terms of your choice, provided that you also meet, for each linked
 independent module, the terms and conditions of the license of that
 module.  An independent module is a module which is not derived from
 or based on this library.  If you modify this library, you may extend
 this exception to your version of the library, but you are not
 obligated to do so.  If you do not wish to do so, delete this
 exception statement from your version. */

/* Written using "Java Class Libraries", 2nd edition, ISBN 0-201-31002-3
 * hashCode algorithm taken from JDK 1.2 docs.
 */

// Source ported to C# from: http://fuseyism.com/classpath/doc/java/util/BitSet-source.html

namespace Util
{
    using System;
    using System.Collections.Generic;
    using System.Text;

    /// <summary>
    /// This class can be thought of in two ways.  You can see it as a
    /// vector of bits or as a set of non-negative integers.  The name
    /// <code>BitSet</code> is a bit misleading.
    /// 
    /// It is implemented by a bit vector, but its equally possible to see
    /// it as set of non-negative integer; each integer in the set is
    /// represented by a set bit at the corresponding index.  The size of
    /// this structure is determined by the highest integer in the set.
    /// 
    /// You can union, intersect and build (symmetric) remainders, by
    /// invoking the logical operations and, or, andNot, resp. xor.
    /// 
    /// This implementation is NOT synchronized against concurrent access from
    /// multiple threads. Specifically, if one thread is reading from a bitset
    /// while another thread is simultaneously modifying it, the results are
    /// undefined.
    /// 
    /// author Jochen Hoenicke
    /// author Tom Tromey (tromey@cygnus.com)
    /// author Eric Blake (ebb9@email.byu.edu)
    /// status updated to 1.4
    /// </summary>
    [Serializable]
    public class BitSet : ICloneable
    {
        private const long serialVersionUID = 7997698588986878753L;

        /// <summary>
        /// A common mask.
        /// </summary>
        private const int LONG_MASK = 0x3f;

        /// <summary>
        /// The actual bits.
        /// @serial the i'th bit is in bits[i/64] at position i%64 (where position
        /// 0 is the least significant).
        /// </summary>
        private long[] bits;

        /// <summary>
        /// Create a new empty bit set. All bits are initially false.
        /// </summary>
        public BitSet()
            : this(64)
        {
        }

        /// <summary>
        /// Create a new empty bit set, with a given size.  This
        /// constructor reserves enough space to represent the integers
        /// from <code>0</code> to <code>nbits-1</code>.
        /// </summary>
        /// <param name="nbits">nbits the initial size of the bit set</param>
        public BitSet(int nbits)
        {
            if (nbits < 0) throw new ArgumentOutOfRangeException("nbits may not be negative");

            var length = (uint)nbits >> 6;
            if ((nbits & LONG_MASK) != 0)
                length++;
            bits = new long[length];
        }

        /// <summary>
        /// Performs the logical AND operation on this bit set and the
        /// given <code>set</code>.  This means it builds the intersection
        /// of the two sets.  The result is stored into this bit set.
        /// </summary>
        /// <param name="bs">the second bit set</param>
        public void And(BitSet bs)
        {
            var max = Math.Min(bits.Length, bs.bits.Length);
            int i;
            for (i = 0; i < max; ++i)
                bits[i] &= bs.bits[i];
            while (i < bits.Length)
                bits[i++] = 0;
        }

        /// <summary>
        /// Performs the logical AND operation on this bit set and the
        /// complement of the given <code>bs</code>.  This means it
        /// selects every element in the first set, that isn't in the
        /// second set.  The result is stored into this bit set and is
        /// effectively the set difference of the two.
        /// </summary>
        /// <param name="bs">the second bit set</param>
        public void AndNot(BitSet bs)
        {
            var i = Math.Min(bits.Length, bs.bits.Length);
            while (--i >= 0)
                bits[i] &= ~bs.bits[i];
        }

        /// <summary>
        /// Returns the number of bits set to true.
        /// </summary>
        public int Cardinality()
        {
            uint card = 0;
            for (int i = bits.Length - 1; i >= 0; i--)
            {
                var a = bits[i];
                // Take care of common cases.
                if (a == 0)
                    continue;
                if (a == -1)
                {
                    card += 64;
                    continue;
                }

                // Successively collapse alternating bit groups into a sum.
                a = ((a >> 1) & 0x5555555555555555L) + (a & 0x5555555555555555L);
                a = ((a >> 2) & 0x3333333333333333L) + (a & 0x3333333333333333L);
                var b = (uint)((a >> 32) + a);
                b = ((b >> 4) & 0x0f0f0f0f) + (b & 0x0f0f0f0f);
                b = ((b >> 8) & 0x00ff00ff) + (b & 0x00ff00ff);
                card += ((b >> 16) & 0x0000ffff) + (b & 0x0000ffff);
            }
            return (int)card;
        }

        /// <summary>
        /// Sets all bits in the set to false.
        /// </summary>
        //public void Clear()
        //{
        //    Arrays.Fill(bits, 0);
        //}

        /// <summary>
        /// Removes the integer <code>pos</code> from this set. That is
        /// the corresponding bit is cleared.  If the index is not in the set,
        /// this method does nothing.
        /// </summary>
        /// <param name="pos">a non-negative integer</param>
        public void Clear(int pos)
        {
            var offset = pos >> 6;
            Ensure(offset);
            bits[offset] &= ~(1L << pos);
        }

        /// <summary>
        /// Sets the bits between from (inclusive) and to (exclusive) to false.
        /// </summary>
        /// <param name="from">the start range (inclusive)</param>
        /// <param name="to">the end range (exclusive)</param>
        public void Clear(int from, int to)
        {
            if (from < 0 || from > to)
                throw new ArgumentOutOfRangeException();
            if (from == to)
                return;
            var lo_offset = (uint)from >> 6;
            var hi_offset = (uint)to >> 6;
            Ensure((int)hi_offset);
            if (lo_offset == hi_offset)
            {
                bits[hi_offset] &= ((1L << from) - 1) | (-1L << to);
                return;
            }

            bits[lo_offset] &= (1L << from) - 1;
            bits[hi_offset] &= -1L << to;
            for (int i = (int)lo_offset + 1; i < hi_offset; i++)
                bits[i] = 0;
        }

        /// <summary>
        /// Create a clone of this bit set, that is an instance of the same
        /// class and contains the same elements.  But it doesn't change when
        /// this bit set changes.
        /// </summary>
        /// <returns>the clone of this object.</returns>
        public object Clone()
        {
            try
            {
                var bs = ObjectCopier.Clone(this);
                bs.bits = (long[])bits.Clone();
                return bs;
            }
            catch
            {
                // Impossible to get here.
                return null;
            }
        }

        /// <summary>
        /// Returns true if the <code>obj</code> is a bit set that contains
        /// exactly the same elements as this bit set, otherwise false.
        /// </summary>
        /// <param name="obj">the object to compare to</param>
        /// <returns>true if obj equals this bit set</returns>
        public override bool Equals(object obj)
        {
            if (!(obj.GetType() == typeof(BitSet)))
                return false;

            var bs = (BitSet)obj;
            var max = Math.Min(bits.Length, bs.bits.Length);
            int i;
            for (i = 0; i < max; ++i)
                if (bits[i] != bs.bits[i])
                    return false;

            // If one is larger, check to make sure all extra bits are 0.
            for (int j = i; j < bits.Length; ++j)
                if (bits[j] != 0)
                    return false;
            for (int j = i; j < bs.bits.Length; ++j)
                if (bs.bits[j] != 0)
                    return false;
            return true;
        }

        /// <summary>
        /// Sets the bit at the index to the opposite value.
        /// </summary>
        /// <param name="index">the index of the bit</param>
        public void Flip(int index)
        {
            var offset = index >> 6;
            Ensure(offset);
            bits[offset] ^= 1L << index;
        }

        /// <summary>
        /// Sets a range of bits to the opposite value.
        /// </summary>
        /// <param name="from">the low index (inclusive)</param>
        /// <param name="to">the high index (exclusive)</param>
        public void Flip(int from, int to)
        {
            if (from < 0 || from > to)
                throw new ArgumentOutOfRangeException();
            if (from == to)
                return;
            var lo_offset = (uint)from >> 6;
            var hi_offset = (uint)to >> 6;
            Ensure((int)hi_offset);

            if (lo_offset == hi_offset)
            {
                bits[hi_offset] ^= (-1L << from) & ((1L << to) - 1);
                return;
            }

            bits[lo_offset] ^= -1L << from;
            bits[hi_offset] ^= (1L << to) - 1;
            for (int i = (int)lo_offset + 1; i < hi_offset; i++)
                bits[i] ^= -1;
        }

        /// <summary>
        /// Returns true if the integer <code>bitIndex</code> is in this bit
        /// set, otherwise false.
        /// </summary>
        /// <param name="pos">a non-negative integer</param>
        /// <returns>the value of the bit at the specified position</returns>
        public Boolean Get(int pos)
        {
            var offset = pos >> 6;
            if (offset >= bits.Length)
                return false;
            return (bits[offset] & (1L << pos)) != 0;
        }

        /// <summary>
        /// Returns a new <code>BitSet</code> composed of a range of bits from
        /// this one.
        /// </summary>
        /// <param name="from">the low index (inclusive)</param>
        /// <param name="to">the high index (exclusive)</param>
        /// <returns></returns>
        public BitSet Get(int from, int to)
        {
            if (from < 0 || from > to)
                throw new ArgumentOutOfRangeException();
            var bs = new BitSet(to - from);
            var lo_offset = (uint)from >> 6;
            if (lo_offset >= bits.Length || to == from)
                return bs;

            var lo_bit = from & LONG_MASK;
            var hi_offset = (uint)to >> 6;
            if (lo_bit == 0)
            {
                uint len = Math.Min(hi_offset - lo_offset + 1, (uint)bits.Length - lo_offset);
                Array.Copy(bits, lo_offset, bs.bits, 0, len);
                if (hi_offset < bits.Length)
                    bs.bits[hi_offset - lo_offset] &= (1L << to) - 1;
                return bs;
            }

            var len2 = Math.Min(hi_offset, (uint)bits.Length - 1);
            var reverse = 64 - lo_bit;
            int i;
            for (i = 0; lo_offset < len2; lo_offset++, i++)
                bs.bits[i] = ((bits[lo_offset] >> lo_bit) | (bits[lo_offset + 1] << reverse));
            if ((to & LONG_MASK) > lo_bit)
                bs.bits[i++] = bits[lo_offset] >> lo_bit;
            if (hi_offset < bits.Length)
                bs.bits[i - 1] &= (1L << (to - from)) - 1;
            return bs;
        }

        /// <summary>
        /// Returns a hash code value for this bit set.  The hash code of
        /// two bit sets containing the same integers is identical.  The algorithm
        /// used to compute it is as follows:
        /// 
        /// Suppose the bits in the BitSet were to be stored in an array of
        /// long integers called <code>bits</code>, in such a manner that
        /// bit <code>k</code> is set in the BitSet (for non-negative values
        /// of <code>k</code>) if and only if
        /// 
        /// <code>((k/64) &lt; bits.length)
        /// && ((bits[k/64] & (1L &lt;&lt; (bit % 64))) != 0)
        /// </code>
        /// 
        /// Then the following definition of the GetHashCode method
        /// would be a correct implementation of the actual algorithm:
        /// 
        /// <pre>public override int GetHashCode()
        /// {
        ///   long h = 1234;
        ///   for (int i = bits.length-1; i &gt;= 0; i--)
        ///   {
        ///     h ^= bits[i] * (i + 1);
        ///   }
        ///   
        ///   return (int)((h >> 32) ^ h);
        /// }</pre>
        /// 
        /// Note that the hash code values changes, if the set is changed.
        /// </summary>
        /// <returns>the hash code value for this bit set.</returns>
        public override int GetHashCode()
        {
            long h = 1234;
            for (int i = bits.Length; i > 0;)
                h ^= i * bits[--i];
            return (int)((h >> 32) ^ h);
        }

        /// <summary>
        /// Returns true if the specified BitSet and this one share at least one
        /// common true bit.
        /// </summary>
        /// <param name="set">the set to check for intersection</param>
        /// <returns>true if the sets intersect</returns>
        public bool Intersects(BitSet set)
        {
            var i = Math.Min(bits.Length, set.bits.Length);
            while (--i >= 0)
            {
                if ((bits[i] & set.bits[i]) != 0)
                    return true;
            }
            return false;
        }

        /// <summary>
        /// Returns true if this set contains no true bits.
        /// </summary>
        /// <returns>true if all bits are false</returns>
        public bool IsEmpty()
        {
            for (int i = bits.Length - 1; i >= 0; i--)
            {
                if (bits[i] != 0)
                    return false;
            }
            return true;
        }

        /// <summary>
        /// Gets the logical number of bits actually used by this bit
        /// set.  It returns the index of the highest set bit plus one.
        /// Note that this method doesn't return the number of set bits.
        /// 
        /// Returns the index of the highest set bit plus one.
        /// </summary>
        public int Length
        {
            get
            {
                // Set i to highest index that contains a non-zero value.
                int i;
                for (i = bits.Length - 1; i >= 0 && bits[i] == 0; --i) { }

                // if i < 0 all bits are cleared.
                if (i < 0)
                    return 0;

                // Now determine the exact length.
                var b = bits[i];
                var len = (i + 1) * 64;
                // b >= 0 checks if the highest bit is zero.
                while (b >= 0)
                {
                    --len;
                    b <<= 1;
                }

                return len;
            }
        }

        /// <summary>
        /// Returns the index of the next false bit, from the specified bit
        /// (inclusive).
        /// </summary>
        /// <param name="from">the start location</param>
        /// <returns>the first false bit</returns>
        public int NextClearBit(int from)
        {
            var offset = from >> 6;
            var mask = 1L << from;
            while (offset < bits.Length)
            {
                var h = bits[offset];
                do
                {
                    if ((h & mask) == 0)
                        return from;
                    mask <<= 1;
                    from++;
                }
                while (mask != 0);

                mask = 1;
                offset++;
            }

            return from;
        }

        /// <summary>
        /// Returns the index of the next true bit, from the specified bit
        /// (inclusive). If there is none, -1 is returned. You can iterate over
        /// all true bits with this loop:<br>
        /// 
        /// <pre>for (int i = bs.nextSetBit(0); i &gt;= 0; i = bs.nextSetBit(i + 1))
        /// {
        ///   // operate on i here
        /// }
        /// </pre>
        /// </summary>
        /// <param name="from">the start location</param>
        /// <returns>the first true bit, or -1</returns>
        public int NextSetBit(int from)
        {
            var offset = from >> 6;
            var mask = 1L << from;
            while (offset < bits.Length)
            {
                var h = bits[offset];
                do
                {
                    if ((h & mask) != 0)
                        return from;
                    mask <<= 1;
                    from++;
                }
                while (mask != 0);

                mask = 1;
                offset++;
            }

            return -1;
        }

        /// <summary>
        /// Performs the logical OR operation on this bit set and the
        /// given <code>set</code>.  This means it builds the union
        /// of the two sets.  The result is stored into this bit set, which
        /// grows as necessary.
        /// </summary>
        /// <param name="bs">the second bit set</param>
        public void Or(BitSet bs)
        {
            Ensure(bs.bits.Length - 1);
            for (int i = bs.bits.Length - 1; i >= 0; i--)
                bits[i] |= bs.bits[i];
        }

        /// <summary>
        /// Add the integer <code>bitIndex</code> to this set.  That is
        /// the corresponding bit is set to true.  If the index was already in
        /// the set, this method does nothing.  The size of this structure
        /// is automatically increased as necessary.
        /// </summary>
        /// <param name="pos">a non-negative integer.</param>
        public void Set(int pos)
        {
            var offset = pos >> 6;
            Ensure(offset);
            bits[offset] |= 1L << pos;
        }

        /// <summary>
        /// Sets the bit at the given index to the specified value. The size of
        /// this structure is automatically increased as necessary.
        /// </summary>
        /// <param name="index">the position to set</param>
        /// <param name="value">the value to set it to</param>
        public void Set(int index, bool value)
        {
            if (value)
                Set(index);
            else
                Clear(index);
        }

        /// <summary>
        /// Sets the bits between from (inclusive) and to (exclusive) to true.
        /// </summary>
        /// <param name="from">the start range (inclusive)</param>
        /// <param name="to">the end range (exclusive)</param>
        public void Set(int from, int to)
        {
            if (from < 0 || from > to)
                throw new ArgumentOutOfRangeException();
            if (from == to)
                return;
            var lo_offset = (uint)from >> 6;
            var hi_offset = (uint)to >> 6;
            Ensure((int)hi_offset);
            if (lo_offset == hi_offset)
            {
                bits[hi_offset] |= (-1L << from) & ((1L << to) - 1);
                return;
            }

            bits[lo_offset] |= -1L << from;
            bits[hi_offset] |= (1L << to) - 1;
            for (int i = (int)lo_offset + 1; i < hi_offset; i++)
                bits[i] = -1;
        }

        /// <summary>
        /// Sets the bits between from (inclusive) and to (exclusive) to the
        /// specified value.
        /// </summary>
        /// <param name="from">the start range (inclusive)</param>
        /// <param name="to">the end range (exclusive)</param>
        /// <param name="value">the value to set it to</param>
        public void Set(int from, int to, bool value)
        {
            if (value)
                Set(from, to);
            else
                Clear(from, to);
        }

        /// <summary>
        /// Returns the number of bits actually used by this bit set.  Note
        /// that this method doesn't return the number of set bits, and that
        /// future requests for larger bits will make this automatically grow.
        /// 
        /// Returns the number of bits currently used.
        /// </summary>
        public int Size
        {
            get
            {
                return bits.Length * 64;
            }
        }

        /// <summary>
        /// Returns the string representation of this bit set.  This
        /// consists of a comma separated list of the integers in this set
        /// surrounded by curly braces.  There is a space after each comma.
        /// A sample string is thus "{1, 3, 53}".
        /// </summary>
        /// <returns>the string representation.</returns>
        public override string ToString()
        {
            var r = new StringBuilder("{");
            var first = true;
            for (int i = 0; i < bits.Length; ++i)
            {
                var bit = 1;
                var word = bits[i];
                if (word == 0)
                    continue;
                for (int j = 0; j < 64; ++j)
                {
                    if ((word & bit) != 0)
                    {
                        if (!first)
                            r.Append(", ");
                        r.Append(64 * i + j);
                        first = false;
                    }
                    bit <<= 1;
                }
            }

            return r.Append("}").ToString();
        }

        /// <summary>
        /// Performs the logical XOR operation on this bit set and the
        /// given <code>set</code>.  This means it builds the symmetric
        /// remainder of the two sets (the elements that are in one set,
        /// but not in the other).  The result is stored into this bit set,
        /// which grows as necessary.
        /// </summary>
        /// <param name="bs">the second bit set</param>
        public void XOr(BitSet bs)
        {
            Ensure(bs.bits.Length - 1);
            for (int i = bs.bits.Length - 1; i >= 0; i--)
                bits[i] ^= bs.bits[i];
        }

        /// <summary>
        /// Make sure the vector is big enough.
        /// </summary>
        /// <param name="lastElt">the size needed for the bits array</param>
        private void Ensure(int lastElt)
        {
            if (lastElt >= bits.Length)
            {
                var nd = new long[lastElt + 1];
                Array.Copy(bits, 0, nd, 0, bits.Length);
                bits = nd;
            }
        }

        // This is used by EnumSet for efficiency.
        public bool ContainsAll(BitSet other)
        {
            for (int i = other.bits.Length - 1; i >= 0; i--)
            {
                if ((bits[i] & other.bits[i]) != other.bits[i])
                    return false;
            }

            return true;
        }
    }
}