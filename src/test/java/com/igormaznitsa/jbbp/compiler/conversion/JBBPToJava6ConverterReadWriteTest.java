/*
 * Copyright 2017 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.jbbp.compiler.conversion;

import com.igormaznitsa.jbbp.JBBPCustomFieldTypeProcessor;
import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;
import com.igormaznitsa.jbbp.compiler.tokenizer.JBBPFieldTypeParameterContainer;
import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitOrder;
import com.igormaznitsa.jbbp.io.JBBPBitOutputStream;
import com.igormaznitsa.jbbp.model.JBBPAbstractField;
import com.igormaznitsa.jbbp.model.JBBPFieldArrayInt;
import com.igormaznitsa.jbbp.model.JBBPFieldInt;
import com.igormaznitsa.jbbp.testaux.AbstractJavaClassCompilerTest;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.igormaznitsa.jbbp.TestUtils.*;
import static org.junit.Assert.*;

/**
 * Test reading writing with converted classes from parser.
 */
public class JBBPToJava6ConverterReadWriteTest extends AbstractJavaClassCompilerTest {

    private byte[] loadResource(final String name) throws Exception {
        final InputStream result = this.getClass().getClassLoader().getResourceAsStream("com/igormaznitsa/jbbp/it/" + name);
        try {
            if (result == null) {
                throw new NullPointerException("Can't find resource '" + name + '\'');
            }
            return IOUtils.toByteArray(result);
        } finally {
            IOUtils.closeQuietly(result);
        }
    }

    @Test
    public void testReadWrite_BooleanArrayWholeStream() throws Exception {
        final Object instance = compileAndMakeInstance("bool [_] boolArray;");
        assertNull("by default must be null", getField(instance, "boolarray", boolean[].class));
        final byte[] etalon = new byte[]{1, 0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1};
        callRead(instance, etalon.clone());
        assertArrayEquals(new boolean[]{true, false, true, true, false, true, true, true, false, false, false, true, true, true, true}, getField(instance, "boolarray", boolean[].class));
        assertArrayEquals(etalon, callWrite(instance));
    }

    @Test
    public void testReadWite_ByteArrayWholeStream() throws Exception {
        final Object instance = compileAndMakeInstance("byte [_] byteArray;");
        assertNull("by default must be null", getField(instance, "bytearray", byte[].class));

        final byte[] etalon = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 22, 33, 44, 55, 66};

        callRead(instance, etalon.clone());

        assertArrayEquals(etalon, getField(instance, "bytearray", byte[].class));
        assertArrayEquals(etalon, callWrite(instance));
    }

    @Test
    public void testReadWite_BitArrayWholeStream() throws Exception {
        final Object instance = compileAndMakeInstance("bit [_] bitArray;");
        assertNull("by default must be null", getField(instance, "bitarray", byte[].class));

        final byte[] etalon = new byte[1024];
        RND.nextBytes(etalon);

        callRead(instance, etalon.clone());

        assertEquals(etalon.length * 8, getField(instance, "bitarray", byte[].class).length);
        assertArrayEquals(etalon, callWrite(instance));
    }

    @Test
    public void testReadWite_PNG() throws Exception {
        final Object instance = compileAndMakeInstance("long header;"
                + "// chunks\n"
                + "chunk [_]{"
                + "   int length; "
                + "   int type; "
                + "   byte[length] data; "
                + "   int crc;"
                + "}");
        final byte[] pngEtalon = loadResource("picture.png");
        final String[] chunkNames = new String[]{"IHDR", "gAMA", "bKGD", "pHYs", "tIME", "tEXt", "IDAT", "IEND"};
        final int[] chunkSizes = new int[]{0x0D, 0x04, 0x06, 0x09, 0x07, 0x19, 0x0E5F, 0x00};

        callRead(instance, pngEtalon.clone());

        assertEquals(0x89504E470D0A1A0AL, getField(instance, "header", Long.class).longValue());
        assertEquals(chunkNames.length, getField(instance, "chunk", Object[].class).length);

        int i = 0;
        for (final Object chunk : getField(instance, "chunk", Object[].class)) {
            assertPngChunk(chunkNames[i], chunkSizes[i], getField(chunk, "type", Integer.class), getField(chunk, "length", Integer.class), getField(chunk, "crc", Integer.class), getField(chunk, "data", byte[].class));
            i++;
        }

        assertArrayEquals(pngEtalon, callWrite(instance));
    }

    @Test
    public void testReadWite_WAV() throws Exception {
        final Object instance = compileAndMakeInstance("<int ChunkID;"
                + "<int ChunkSize;"
                + "<int Format;"
                + "SubChunks [_]{"
                + "  <int SubChunkID;"
                + "  <int SubChunkSize;"
                + "  byte [SubChunkSize] data;"
                + "  align:2;"
                + "}");

        final byte[] wavEtalon = loadResource("M1F1-float64WE-AFsp.wav");
        final String[] subchunkNames = new String[]{"fmt ", "fact", "data", "afsp", "LIST"};

        callRead(instance, wavEtalon.clone());

        assertEquals(0x46464952, getField(instance, "chunkid", Integer.class).intValue());
        assertEquals(0x45564157, getField(instance, "format", Integer.class).intValue());

        final Object[] subchunks = getField(instance, "subchunks", Object[].class);
        assertEquals("Number of parsed subchunks must be [" + subchunkNames.length + ']', subchunkNames.length, subchunks.length);

        int calculatedSize = 4;
        int index = 0;
        for (final Object subchunk : subchunks) {
            final String strChunkId = subchunkNames[index++];
            assertEquals("WAV subchunk must have 4 char length [" + strChunkId + ']', 4, strChunkId.length());
            assertEquals(strChunkId, wavInt2Str(getField(subchunk, "subchunkid", Integer.class)));
            final int subChunkSize = getField(subchunk, "subchunksize", Integer.class);
            assertEquals("Data array must have the same size as described in sub-chunk size field", subChunkSize, getField(subchunk, "data", byte[].class).length);
            calculatedSize += subChunkSize + 8 + (subChunkSize & 1);
        }

        assertEquals(calculatedSize, getField(instance, "chunksize", Integer.class).intValue());

        assertArrayEquals(wavEtalon, callWrite(instance));
    }

    @Test
    public void testReadWrite_SNA() throws Exception {
        final Object instance = compileAndMakeInstance("ubyte regI;"
                + "<ushort altHL; <ushort altDE; <ushort altBC; <ushort altAF;"
                + "<ushort regHL; <ushort regDE; <ushort regBC; <ushort regIY; <ushort regIX;"
                + "ubyte iff; ubyte regR;"
                + "<ushort regAF; <ushort regSP;"
                + "ubyte im;"
                + "ubyte borderColor;"
                + "byte [49152] ramDump;");

        final byte[] snaEtalon = loadResource("zexall.sna");

        callRead(instance, snaEtalon.clone());

        assertEquals(0x3F, getField(instance, "regi", Character.class).charValue());
        assertEquals(0x2758, getField(instance, "althl", Character.class).charValue());
        assertEquals(0x369B, getField(instance, "altde", Character.class).charValue());
        assertEquals(0x1721, getField(instance, "altbc", Character.class).charValue());
        assertEquals(0x0044, getField(instance, "altaf", Character.class).charValue());

        assertEquals(0x2D2B, getField(instance, "reghl", Character.class).charValue());
        assertEquals(0x80ED, getField(instance, "regde", Character.class).charValue());
        assertEquals(0x803E, getField(instance, "regbc", Character.class).charValue());
        assertEquals(0x5C3A, getField(instance, "regiy", Character.class).charValue());
        assertEquals(0x03D4, getField(instance, "regix", Character.class).charValue());

        assertEquals(0x00, getField(instance, "iff", Character.class).charValue());
        assertEquals(0x0AE, getField(instance, "regr", Character.class).charValue());

        assertEquals(0x14A1, getField(instance, "regaf", Character.class).charValue());
        assertEquals(0x7E62, getField(instance, "regsp", Character.class).charValue());

        assertEquals(0x01, getField(instance, "im", Character.class).charValue());
        assertEquals(0x07, getField(instance, "bordercolor", Character.class).charValue());

        assertEquals(49152, getField(instance, "ramdump", byte[].class).length);

        assertArrayEquals(snaEtalon, callWrite(instance));
    }

    @Test
    public void testReadWrite_TGA_noColormap() throws Exception {
        final Object instance = compileAndMakeInstance("Header {"
                + "          ubyte IDLength;"
                + "          ubyte ColorMapType;"
                + "          ubyte ImageType;"
                + "          <ushort CMapStart;"
                + "          <ushort CMapLength;"
                + "          ubyte CMapDepth;"
                + "          <short XOffset;"
                + "          <short YOffset;"
                + "          <ushort Width;"
                + "          <ushort Height;"
                + "          ubyte PixelDepth;"
                + "          ImageDesc {"
                + "              bit:4 PixelAttrNumber;"
                + "              bit:2 Pos;"
                + "              bit:2 Reserved;"
                + "          }"
                + "      }"
                + "byte [Header.IDLength] ImageID;"
                + "ColorMap [ (Header.ColorMapType & 1) * Header.CMapLength ] {"
                + "    byte [Header.CMapDepth >>> 3] ColorMapItem; "
                + " }"
                + "byte [_] ImageData;");

        final byte[] tgaEtalon = loadResource("cbw8.tga");

        callRead(instance, tgaEtalon.clone());

        assertEquals("Truevision(R) Sample Image".length(), getField(instance, "imageid", byte[].class).length);
        assertEquals(128, getField(instance, "header.width", Character.class).charValue());
        assertEquals(128, getField(instance, "header.height", Character.class).charValue());
        assertEquals(8, getField(instance, "header.pixeldepth", Character.class).charValue());
        assertEquals(0, getField(instance, "colormap", Object[].class).length);
        assertEquals(8715, getField(instance, "imagedata", byte[].class).length);

        assertArrayEquals(tgaEtalon, callWrite(instance));
    }

    @Test
    public void testReadWrite_TGA_hasColormap() throws Exception {
        final Object instance = compileAndMakeInstance("Header {"
                + "          ubyte IDLength;"
                + "          ubyte ColorMapType;"
                + "          ubyte ImageType;"
                + "          <ushort CMapStart;"
                + "          <ushort CMapLength;"
                + "          ubyte CMapDepth;"
                + "          <short XOffset;"
                + "          <short YOffset;"
                + "          <ushort Width;"
                + "          <ushort Height;"
                + "          ubyte PixelDepth;"
                + "          ImageDesc {"
                + "              bit:4 PixelAttrNumber;"
                + "              bit:2 Pos;"
                + "              bit:2 Reserved;"
                + "          }"
                + "      }"
                + "byte [Header.IDLength] ImageID;"
                + "ColorMap [ (Header.ColorMapType & 1) * Header.CMapLength ] {"
                + "    byte [Header.CMapDepth >>> 3] ColorMapItem; "
                + " }"
                + "byte [_] ImageData;");

        final byte[] tgaEtalon = loadResource("indexedcolor.tga");

        callRead(instance, tgaEtalon.clone());

        assertEquals("".length(), getField(instance, "imageid", byte[].class).length);
        assertEquals(640, getField(instance, "header.width", Character.class).charValue());
        assertEquals(480, getField(instance, "header.height", Character.class).charValue());
        assertEquals(8, getField(instance, "header.pixeldepth", Character.class).charValue());
        assertEquals(256, getField(instance, "colormap", Object[].class).length);
        assertEquals(155403, getField(instance, "imagedata", byte[].class).length);

        assertArrayEquals(tgaEtalon, callWrite(instance));
    }

    @Test
    public void testReadWrite_Z80v1() throws Exception {
        final Object instance = compileAndMakeInstance("byte reg_a; byte reg_f; <short reg_bc; <short reg_hl; <short reg_pc; <short reg_sp; byte reg_ir; byte reg_r; "
                + "flags{ bit:1 reg_r_bit7; bit:3 bordercolor; bit:1 basic_samrom; bit:1 compressed; bit:2 nomeaning;}"
                + "<short reg_de; <short reg_bc_alt; <short reg_de_alt; <short reg_hl_alt; byte reg_a_alt; byte reg_f_alt; <short reg_iy; <short reg_ix; byte iff; byte iff2;"
                + "emulFlags{bit:2 interruptmode; bit:1 issue2emulation; bit:1 doubleintfreq; bit:2 videosync; bit:2 inputdevice;}"
                + "byte [_] data;");

        final byte[] z80Etalon = loadResource("test.z80");

        callRead(instance, z80Etalon.clone());

        assertEquals((byte) 0x7E, getField(instance, "reg_a", Byte.class).byteValue());
        assertEquals((byte) 0x86, getField(instance, "reg_f", Byte.class).byteValue());
        assertEquals((short) 0x7A74, getField(instance, "reg_bc", Short.class).shortValue());
        assertEquals((short) 0x7430, getField(instance, "reg_hl", Short.class).shortValue());

        assertEquals((short) 12198, getField(instance, "reg_pc", Short.class).shortValue());
        assertEquals((short) 65330, getField(instance, "reg_sp", Short.class).shortValue());

        assertEquals((byte) 0x3F, getField(instance, "reg_ir", Byte.class).byteValue());
        assertEquals((byte) 0x1A, getField(instance, "reg_r", Byte.class).byteValue());

        assertEquals((byte) 0, getField(instance, "flags.reg_r_bit7", Byte.class).byteValue());
        assertEquals((byte) 2, getField(instance, "flags.bordercolor", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "flags.basic_samrom", Byte.class).byteValue());
        assertEquals((byte) 1, getField(instance, "flags.compressed", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "flags.nomeaning", Byte.class).byteValue());

        assertEquals((short) 0x742B, getField(instance, "reg_de", Short.class).shortValue());
        assertEquals((short) 0x67C6, getField(instance, "reg_bc_alt", Short.class).shortValue());
        assertEquals((short) 0x3014, getField(instance, "reg_de_alt", Short.class).shortValue());
        assertEquals((short) 0x3461, getField(instance, "reg_hl_alt", Short.class).shortValue());

        assertEquals((byte) 0x00, getField(instance, "reg_a_alt", Byte.class).byteValue());
        assertEquals((byte) 0x46, getField(instance, "reg_f_alt", Byte.class).byteValue());

        assertEquals((short) 0x5C3A, getField(instance, "reg_iy", Short.class).shortValue());
        assertEquals((short) 0x03D4, getField(instance, "reg_ix", Short.class).shortValue());

        assertEquals((byte) 0xFF, getField(instance, "iff", Byte.class).byteValue());
        assertEquals((byte) 0xFF, getField(instance, "iff2", Byte.class).byteValue());

        assertEquals((byte) 1, getField(instance, "emulflags.interruptmode", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "emulflags.issue2emulation", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "emulflags.doubleintfreq", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "emulflags.videosync", Byte.class).byteValue());
        assertEquals((byte) 0, getField(instance, "emulflags.inputdevice", Byte.class).byteValue());

        assertEquals(12399, getField(instance, "data", byte[].class).length);

        assertArrayEquals(z80Etalon, callWrite(instance));
    }

    @Test
    public void testReadWrite_CustomField() throws Exception {
        final Object klazz = compileAndMakeInstance("com.igormaznitsa.jbbp.test.CustomFieldParser", "threebyte one;<threebyte two; threebyte:2 [one+two] arrayone; <threebyte:3 [_] arraytwo;", new JBBPCustomFieldTypeProcessor() {
            private final String[] names = new String[]{"threebyte"};

            @Override
            public String[] getCustomFieldTypes() {
                return names;
            }

            @Override
            public boolean isAllowed(JBBPFieldTypeParameterContainer fieldType, String fieldName, int extraData, boolean isArray) {
                return true;
            }

            @Override
            public JBBPAbstractField readCustomFieldType(JBBPBitInputStream in, JBBPBitOrder bitOrder, int parserFlags, JBBPFieldTypeParameterContainer customTypeFieldInfo, JBBPNamedFieldInfo fieldName, int extraData, boolean readWholeStream, int arrayLength) throws IOException {
                fail("Must not be called");
                return null;
            }
        }, new JavaClassContent("com.igormaznitsa.jbbp.test.CustomFieldParser", "package com.igormaznitsa.jbbp.test;\n"
                + "import com.igormaznitsa.jbbp.model.*;\n"
                + "import com.igormaznitsa.jbbp.io.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.tokenizer.*;\n"
                + "import java.io.IOException;\n"
                + "import java.util.*;\n"
                + ""
                + "public class CustomFieldParser extends " + PACKAGE_NAME + '.' + CLASS_NAME + "{"
                + " private int readThree(JBBPBitInputStream in, JBBPByteOrder byteOrder) throws IOException {"
                + "   int a = in.readByte();"
                + "   int b = in.readByte();"
                + "   int c = in.readByte();"
                + "   return byteOrder == JBBPByteOrder.BIG_ENDIAN ? (a << 16) | (b << 8) | c : (c << 16) | (b << 8) | a;"
                + " }"
                + " private void writeThree(JBBPBitOutputStream out, JBBPByteOrder byteOrder, int value) throws IOException {"
                + "   int c = value & 0xFF; int b = (value >> 8) & 0xFF; int a = (value >> 16) & 0xFF;"
                + "   if (byteOrder == JBBPByteOrder.BIG_ENDIAN) {"
                + "     out.write(a); out.write(b); out.write(c);"
                + "   } else {"
                + "      out.write(c); out.write(b); out.write(a);"
                + "   }"
                + " }"
                + " public JBBPAbstractField readCustomFieldType(Object sourceStruct, JBBPBitInputStream inStream, JBBPFieldTypeParameterContainer typeParameterContainer, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue, boolean readWholeStream, int arraySize) throws IOException{"
                + "   if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "   if (readWholeStream || arraySize>=0) {"
                + "      if (readWholeStream) {"
                + "         if (extraValue!=3) throw new Error(\"must be 3\");"
                + "         com.igormaznitsa.jbbp.utils.DynamicIntBuffer buffer = new com.igormaznitsa.jbbp.utils.DynamicIntBuffer();"
                + "         while(inStream.hasAvailableData()){ buffer.write(readThree(inStream, typeParameterContainer.getByteOrder())); }"
                + "         return new JBBPFieldArrayInt(nullableNamedFieldInfo, buffer.toIntArray());"
                + "      } else {"
                + "         if (extraValue!=2) throw new Error(\"must be 2\");"
                + "        int [] arra = new int[arraySize];"
                + "        for (int i=0;i<arraySize;i++){ arra [i] = readThree(inStream, typeParameterContainer.getByteOrder()); }"
                + "        return new JBBPFieldArrayInt(nullableNamedFieldInfo, arra);"
                + "      }"
                + "   } else {"
                + "      if (extraValue!=0) throw new Error(\"must be 0\");"
                + "      return new JBBPFieldInt(nullableNamedFieldInfo, readThree(inStream, typeParameterContainer.getByteOrder()));"
                + "   }"
                + " }"
                + " public void writeCustomFieldType(Object sourceStruct, JBBPBitOutputStream outStream, JBBPAbstractField fieldValue, JBBPFieldTypeParameterContainer typeParameterContainer, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue, boolean wholeArray, int arraySize) throws IOException {"
                + "   if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "   if (arraySize>=0 || wholeArray) {"
                + "     if (wholeArray && extraValue!=3) throw new Error(\"wrong extra\");"
                + "     if (arraySize>=0 && extraValue!=2) throw new Error(\"wrong extra\");"
                + "     int [] arra = ((JBBPFieldArrayInt) fieldValue).getArray();"
                + "     int len = wholeArray ? arra.length : arraySize;"
                + "     for(int i=0;i<len;i++) { writeThree(outStream, typeParameterContainer.getByteOrder(), arra[i]);}"
                + "   } else {"
                + "     if (extraValue!=0) throw new Error(\"must be 0\");"
                + "     writeThree(outStream, typeParameterContainer.getByteOrder(), ((JBBPFieldInt)fieldValue).getAsInt());"
                + "   }"
                + " }"
                + "}"));

        final byte[] etalonArray = new byte[6 + (0x7B + 0x1CB) * 3 + 112 * 3];
        RND.nextBytes(etalonArray);
        etalonArray[0] = 0x00;
        etalonArray[1] = 0x00;
        etalonArray[2] = (byte) 0x7B;
        etalonArray[3] = (byte) 0xCB;
        etalonArray[4] = 0x01;
        etalonArray[5] = 0x00;

        callRead(klazz, etalonArray.clone());
        final JBBPFieldInt one = getField(klazz, "one", JBBPFieldInt.class);
        assertEquals(0x7B, one.getAsInt());

        final JBBPFieldInt two = getField(klazz, "two", JBBPFieldInt.class);
        assertEquals(0x01CB, two.getAsInt());

        final JBBPFieldArrayInt arrayone = getField(klazz, "arrayone", JBBPFieldArrayInt.class);
        assertEquals(0x7B + 0x01CB, arrayone.getArray().length);


        final JBBPFieldArrayInt arraytwo = getField(klazz, "arraytwo", JBBPFieldArrayInt.class);
        assertEquals(112, arraytwo.getArray().length);

        assertArrayEquals(etalonArray, callWrite(klazz));
    }

    @Test
    public void testReadWrite_VarFields() throws Exception {
        final Object klazz = compileAndMakeInstance("com.igormaznitsa.jbbp.test.VarFieldParser", "var:12 one;<var:12 two; var:4 [18] arrayone; <var:4 [_] arraytwo;", null, new JavaClassContent("com.igormaznitsa.jbbp.test.VarFieldParser", "package com.igormaznitsa.jbbp.test;\n"
                + "import com.igormaznitsa.jbbp.model.*;\n"
                + "import com.igormaznitsa.jbbp.io.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.tokenizer.*;\n"
                + "import java.io.IOException;\n"
                + "import java.util.*;\n"
                + ""
                + "public class VarFieldParser extends " + PACKAGE_NAME + '.' + CLASS_NAME + "{"
                + " private int readThree(JBBPBitInputStream in, JBBPByteOrder byteOrder) throws IOException {"
                + "   int a = in.readByte();"
                + "   int b = in.readByte();"
                + "   int c = in.readByte();"
                + "   return byteOrder == JBBPByteOrder.BIG_ENDIAN ? (a << 16) | (b << 8) | c : (c << 16) | (b << 8) | a;"
                + " }"
                + " private void writeThree(JBBPBitOutputStream out, JBBPByteOrder byteOrder, int value) throws IOException {"
                + "   int c = value & 0xFF; int b = (value >> 8) & 0xFF; int a = (value >> 16) & 0xFF;"
                + "   if (byteOrder == JBBPByteOrder.BIG_ENDIAN) {"
                + "     out.write(a); out.write(b); out.write(c);"
                + "   } else {"
                + "      out.write(c); out.write(b); out.write(a);"
                + "   }"
                + " }"
                + "public JBBPAbstractField readVarField(Object sourceStruct, JBBPBitInputStream inStream, JBBPByteOrder byteOrder, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue) throws IOException{"
                + "   if (extraValue!=12) throw new Error(\"wrong extra\");"
                + "   return new JBBPFieldInt(nullableNamedFieldInfo, readThree(inStream, byteOrder));"
                + "}"
                + "public JBBPAbstractArrayField<? extends JBBPAbstractField> readVarArray(Object sourceStruct, JBBPBitInputStream inStream, JBBPByteOrder byteOrder, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue, boolean readWholeStream, int arraySize) throws IOException {"
                + "   if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "   if (extraValue!=4) throw new Error(\"wrong extra\");"
                + "   if (readWholeStream) {"
                + "         com.igormaznitsa.jbbp.utils.DynamicIntBuffer buffer = new com.igormaznitsa.jbbp.utils.DynamicIntBuffer();"
                + "         while(inStream.hasAvailableData()){ buffer.write(readThree(inStream, byteOrder)); }"
                + "         return new JBBPFieldArrayInt(nullableNamedFieldInfo, buffer.toIntArray());"
                + "      } else {"
                + "        int [] arra = new int[arraySize];"
                + "        for (int i=0;i<arraySize;i++){ arra [i] = readThree(inStream, byteOrder); }"
                + "        return new JBBPFieldArrayInt(nullableNamedFieldInfo, arra);"
                + "      }"
                + "}"
                + "public void writeVarField(Object sourceStruct, JBBPAbstractField value, JBBPBitOutputStream outStream, JBBPByteOrder byteOrder, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue) throws IOException{"
                + "   if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "   if (extraValue!=12) throw new Error(\"wrong extra\");"
                + "     writeThree(outStream, byteOrder, ((JBBPFieldInt)value).getAsInt());"
                + "}"
                + "public void writeVarArray(Object sourceStruct, JBBPAbstractArrayField<? extends JBBPAbstractField> array, JBBPBitOutputStream outStream, JBBPByteOrder byteOrder, JBBPNamedFieldInfo nullableNamedFieldInfo, int extraValue, int arraySizeToWrite) throws IOException{"
                + "   if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "   if (extraValue!=4) throw new Error(\"wrong extra\");"
                + "     int [] arra = ((JBBPFieldArrayInt) array).getArray();"
                + "     int len = arraySizeToWrite < 0 ? arra.length : arraySizeToWrite;"
                + "     for(int i=0;i<len;i++) { writeThree(outStream, byteOrder, arra[i]);}"
                + "}"
                + "}"));

        final byte[] etalonArray = new byte[1000 * 3];
        int v = 1;
        for (int i = 0; i < etalonArray.length; i++) {
            etalonArray[i] = (byte) (v % 167);
            v++;
        }

        callRead(klazz, etalonArray.clone());
        final JBBPFieldInt one = getField(klazz, "one", JBBPFieldInt.class);
        assertEquals(0x010203, one.getAsInt());

        final JBBPFieldInt two = getField(klazz, "two", JBBPFieldInt.class);
        assertEquals(0x060504, two.getAsInt());

        final JBBPFieldArrayInt arrayone = getField(klazz, "arrayone", JBBPFieldArrayInt.class);
        assertEquals(18, arrayone.getArray().length);

        final JBBPFieldArrayInt arraytwo = getField(klazz, "arraytwo", JBBPFieldArrayInt.class);
        assertEquals(980, arraytwo.getArray().length);

        assertArrayEquals(etalonArray, callWrite(klazz));
    }

    @Test
    public void testReadWrite_NamedExternalFieldInExpression() throws Exception {
        final Object klazz = compileAndMakeInstance("com.igormaznitsa.jbbp.test.ExtraFieldParser", "byte [$one*$two] data;", null, new JavaClassContent("com.igormaznitsa.jbbp.test.ExtraFieldParser", "package com.igormaznitsa.jbbp.test;\n"
                + "import com.igormaznitsa.jbbp.model.*;\n"
                + "import com.igormaznitsa.jbbp.io.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.*;\n"
                + "import com.igormaznitsa.jbbp.compiler.tokenizer.*;\n"
                + "import java.io.IOException;\n"
                + "import java.util.*;\n"
                + "public class ExtraFieldParser extends " + PACKAGE_NAME + '.' + CLASS_NAME + "{"
                + "    public int getNamedValue(Object sourceStruct, String valueName){"
                + "      if (sourceStruct == null) throw new Error(\"Struct must not be null\");"
                + "      if (\"one\".equals(valueName)) return 11;"
                + "      if (\"two\".equals(valueName)) return 7;"
                + "      throw new Error(\"Unexpected value \"+valueName);"
                + "   }"
                + "}"));

        final byte[] array = new byte[77];

        RND.nextBytes(array);
        final JBBPBitInputStream in = new JBBPBitInputStream(new ByteArrayInputStream(array.clone()));

        callRead(klazz, in);
        assertFalse(in.hasAvailableData());
        assertEquals(77, getField(klazz, "data", byte[].class).length);

        assertArrayEquals(array, callWrite(klazz));
    }

    @Test
    public void testReadWrite_NetPacket() throws Exception {
        final Object ethernetHeader = compileAndMakeInstance("byte[6] MacDestination;"
                + "byte[6] MacSource;"
                + "ushort EtherTypeOrLength;");

        final Object ipHeader = compileAndMakeInstance("bit:4 InternetHeaderLength;"
                + "bit:4 Version;"
                + "bit:2 ECN;"
                + "bit:6 DSCP;"
                + "ushort TotalPacketLength;"
                + "ushort Identification;"
                + "ushort IPFlagsAndFragmentOffset;"
                + "ubyte TTL;"
                + "ubyte Protocol;"
                + "ushort HeaderChecksum;"
                + "int SourceAddress;"
                + "int DestinationAddress;"
                + "byte [(InternetHeaderLength-5)*4] Options;");

        final Object tcpHeader = compileAndMakeInstance("ushort SourcePort;"
                + "ushort DestinationPort;"
                + "int SequenceNumber;"
                + "int AcknowledgementNumber;"
                + "bit:1 NONCE;"
                + "bit:3 RESERVED;"
                + "bit:4 HLEN;"
                + "bit:1 FIN;"
                + "bit:1 SYN;"
                + "bit:1 RST;"
                + "bit:1 PSH;"
                + "bit:1 ACK;"
                + "bit:1 URG;"
                + "bit:1 ECNECHO;"
                + "bit:1 CWR;"
                + "ushort WindowSize;"
                + "ushort TCPCheckSum;"
                + "ushort UrgentPointer;"
                + "byte [$$-HLEN*4] Option;");

        final byte[] netPacketEtalon = loadResource("tcppacket.bin");

        final JBBPBitInputStream inStream = new JBBPBitInputStream(new ByteArrayInputStream(netPacketEtalon));

        callRead(ethernetHeader, inStream);
        assertArrayEquals("Destination MAC", new byte[]{(byte) 0x60, (byte) 0x67, (byte) 0x20, (byte) 0xE1, (byte) 0xF9, (byte) 0xF8}, getField(ethernetHeader, "macdestination", byte[].class));
        assertArrayEquals("Source MAC", new byte[]{(byte) 0x00, (byte) 0x26, (byte) 0x44, (byte) 0x74, (byte) 0xFE, (byte) 0x66}, getField(ethernetHeader, "macsource", byte[].class));
        final int etherTypeOrLength = getField(ethernetHeader, "ethertypeorlength", Character.class);
        assertEquals("Ethernet type or length", 0x800, etherTypeOrLength);

        inStream.resetCounter();
        callRead(ipHeader, inStream);

        assertEquals("IP Version", 4, getField(ipHeader, "version", Byte.class).intValue());

        final int internetHeaderLength = getField(ipHeader, "internetheaderlength", Byte.class).intValue();
        assertEquals("Length of the IP header (in 4 byte items)", 5, internetHeaderLength);
        assertEquals("Differentiated Services Code Point", 0, getField(ipHeader, "dscp", Byte.class).intValue());
        assertEquals("Explicit Congestion Notification", 0, getField(ipHeader, "ecn", Byte.class).intValue());

        final int ipTotalPacketLength = getField(ipHeader, "totalpacketlength", Character.class);

        assertEquals("Entire IP packet size, including header and data, in bytes", 159, ipTotalPacketLength);
        assertEquals("Identification", 30810, getField(ipHeader, "identification", Character.class).charValue());

        final int ipFlagsAndFragmentOffset = getField(ipHeader, "ipflagsandfragmentoffset", Character.class);

        assertEquals("Extracted IP flags", 0x2, ipFlagsAndFragmentOffset >>> 13);
        assertEquals("Extracted Fragment offset", 0x00, ipFlagsAndFragmentOffset & 0x1FFF);

        assertEquals("Time To Live", 0x39, getField(ipHeader, "ttl", Character.class).charValue());
        assertEquals("Protocol (RFC-790)", 0x06, getField(ipHeader, "protocol", Character.class).charValue());
        assertEquals("IPv4 Header Checksum", 0x7DB6, getField(ipHeader, "headerchecksum", Character.class).charValue());
        assertEquals("Source IP address", 0xD5C7B393, getField(ipHeader, "sourceaddress", Integer.class).intValue());
        assertEquals("Destination IP address", 0xC0A80145, getField(ipHeader, "destinationaddress", Integer.class).intValue());

        assertEquals(0, getField(ipHeader, "options", byte[].class).length);

        inStream.resetCounter();
        callRead(tcpHeader, inStream);

        assertEquals(40018, getField(tcpHeader, "sourceport", Character.class).charValue());
        assertEquals(56344, getField(tcpHeader, "destinationport", Character.class).charValue());
        assertEquals(0xE0084171, getField(tcpHeader, "sequencenumber", Integer.class).intValue());
        assertEquals(0xAB616F71, getField(tcpHeader, "acknowledgementnumber", Integer.class).intValue());

        assertEquals(0, getField(tcpHeader, "fin", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "syn", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "rst", Byte.class).intValue());
        assertEquals(1, getField(tcpHeader, "psh", Byte.class).intValue());
        assertEquals(1, getField(tcpHeader, "ack", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "urg", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "ecnecho", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "cwr", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "nonce", Byte.class).intValue());
        assertEquals(0, getField(tcpHeader, "reserved", Byte.class).intValue());

        assertEquals(5, getField(tcpHeader, "hlen", Byte.class).intValue());

        assertEquals(40880, getField(tcpHeader, "windowsize", Character.class).charValue());
        assertEquals(0x8BB6, getField(tcpHeader, "tcpchecksum", Character.class).charValue());
        assertEquals(0, getField(tcpHeader, "urgentpointer", Character.class).charValue());

        assertEquals(0, getField(tcpHeader, "option", byte[].class).length);

        final int payloadDataLength = ipTotalPacketLength - (internetHeaderLength * 4) - (int) inStream.getCounter();
        final byte[] data = inStream.readByteArray(payloadDataLength);
        assertEquals(119, data.length);

        assertFalse(inStream.hasAvailableData());

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final JBBPBitOutputStream outStream = new JBBPBitOutputStream(bos);

        callWrite(ethernetHeader, outStream);
        outStream.resetCounter();
        callWrite(ipHeader, outStream);
        outStream.resetCounter();
        callWrite(tcpHeader, outStream);

        outStream.write(data);

        outStream.close();
        assertArrayEquals(netPacketEtalon, bos.toByteArray());
    }

}
