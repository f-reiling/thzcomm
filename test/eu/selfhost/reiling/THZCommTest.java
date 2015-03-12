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
        String msg = "0100340A0101091E1003";
        int expResult = 0x34;
        int result = THZComm.calcThzChecksum(msg);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    @Test
    public void testDecodeThzResponse() throws Exception {
        System.out.println("decodeThzResponse");
        String data = "0100340A0101091E1003";
        String expResult = "0A0101091E";
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

    @Test
    public void testReplaceBytes() {
        System.out.println("replaceBytes");
        String data = "010101";
        String expResult = "010101";
        String result = THZComm.replaceDataString(data,"10","1010");
        assertEquals(expResult, result);
        
        data = "101010";
        expResult = "101010101010";
        result = THZComm.replaceDataString(data,"10","1010");
        assertEquals(expResult, result);
        
        data = "01002B1800";
        expResult = "01002B00";
        result = THZComm.replaceDataString(data,"2B18","2B");
        assertEquals(expResult, result);
        
        data = "01002B00";
        expResult = "01002B1800";
        result = THZComm.replaceDataString(data,"2B","2B18");
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }
    
}
