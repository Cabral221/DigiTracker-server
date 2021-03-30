package org.traccar.protocol;

import org.junit.Test;
import org.traccar.ProtocolTest;

public class L100FrameDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        L100FrameDecoder decoder = new L100FrameDecoder();

        verifyFrame(
                binary("41544c2c4c2c3836383334353033383137313936332c4e2c3230313231382c3039333031362c412c3032352e3036373134342c4e2c3035352e3134343833332c452c3030302e302c4750532c333933392c3432342c30332c30303430352c303038383334"),
                decoder.decode(null, null, binary("41544c2c4c2c3836383334353033383137313936332c4e2c3230313231382c3039333031362c412c3032352e3036373134342c4e2c3035352e3134343833332c452c3030302e302c4750532c333933392c3432342c30332c30303430352c30303838333440")));

        verifyFrame(
                binary("4c2c41544c2c3836363739353033303437373935322c30312c303033352c"),
                decoder.decode(null, null, binary("4c2c41544c2c3836363739353033303437373935322c30312c303033352c2a28")));

        verifyFrame(
                binary("41544c3335363839353033373533333734352c244750524d432c3131313731392e3030302c412c323833382e303034352c4e2c30373731332e333730372c452c302e30302c2c3132303831302c2c2c412a3735242c2330313130303131313030313031302c4e2e432c4e2e432c4e2e432c31323334352e36372c33312e342c342e322c32312c4d43432c4d4e432c4c41432c43656c6c494441544c"),
                decoder.decode(null, null, binary("200141544c3335363839353033373533333734352c244750524d432c3131313731392e3030302c412c323833382e303034352c4e2c30373731332e333730372c452c302e30302c2c3132303831302c2c2c412a3735242c2330313130303131313030313031302c4e2e432c4e2e432c4e2e432c31323334352e36372c33312e342c342e322c32312c4d43432c4d4e432c4c41432c43656c6c494441544c027a")));

        verifyFrame(
                binary("41544c3335363839353033373533333734352c244750524d432c3131313731392e3030302c412c323833382e303034352c4e2c30373731332e333730372c452c302e30302c2c3132303831302c2c2c412a3735244c4f432c436f6e6e61756768742043697263757320c2a0436f6e6e617567687420506c61636520c2a04e65772044656c686920c2a044656c6869c2a0496e6469612c2330313130303130313130313031302c322e332c33352e36372c38302c31323334352e36372c33312e342c342e322c32312c4d43432c4d4e432c4c41432c43656c6c494441544c"),
                decoder.decode(null, null, binary("200341544c3335363839353033373533333734352c244750524d432c3131313731392e3030302c412c323833382e303034352c4e2c30373731332e333730372c452c302e30302c2c3132303831302c2c2c412a3735244c4f432c436f6e6e61756768742043697263757320c2a0436f6e6e617567687420506c61636520c2a04e65772044656c686920c2a044656c6869c2a0496e6469612c2330313130303130313130313031302c322e332c33352e36372c38302c31323334352e36372c33312e342c342e322c32312c4d43432c4d4e432c4c41432c43656c6c494441544c047a")));

    }

}
