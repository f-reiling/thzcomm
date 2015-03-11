/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.selfhost.reiling;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author u2308613
 */
public class THZCommTest {
    
    public THZCommTest() {
    }

    @Test
    public void testEncodeThzDataMsg() {
        System.out.println("encodeThzDataMsg");
        String msg = "ABCDEF102B";
        String expResult = "0100A3ABCDEF10102B181003";
        String result = THZComm.encodeThzDataMsg(msg);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    @Test
    public void testCalcThzChecksum() {
        System.out.println("calcThzChecksum");
        String msg = "0100450A091E1003";
        int expResult = 0x32;
        int result = THZComm.calcThzChecksum(msg);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    @Test
    public void testDecodeThzResponse() throws Exception {
        System.out.println("decodeThzResponse");
        String data = "01003C10102B181003";
        String expResult = "102B";
        String result = THZComm.decodeThzResponse(data);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    @Test
    public void testHexStringToByteArray() {
        System.out.println("hexStringToByteArray");
        String s = "0123456789ABCDEF";
        byte[] expResult = new byte[]{(byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67, (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF};
        byte[] result = THZComm.hexStringToByteArray(s);
        assertArrayEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    @Test
    public void testBytesToHex() {
        System.out.println("bytesToHex");
        byte[] bytes = new byte[]{(byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67, (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF};
        String expResult = "0123456789ABCDEF";
        String result = THZComm.bytesToHex(bytes);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
    
}
