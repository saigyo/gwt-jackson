package com.github.nmorel.gwtjackson.client.ser.date;

import java.sql.Time;

import com.github.nmorel.gwtjackson.client.ser.AbstractJsonSerializerTest;
import com.github.nmorel.gwtjackson.client.ser.BaseDateJsonSerializer.SqlTimeJsonSerializer;

/**
 * @author Nicolas Morel
 */
public class SqlTimeJsonSerializerTest extends AbstractJsonSerializerTest<Time> {

    @Override
    protected SqlTimeJsonSerializer createSerializer() {
        return SqlTimeJsonSerializer.getInstance();
    }

    public void testSerializeValue() {
        Time time = new Time( getUTCTime( 2012, 8, 18, 12, 45, 56, 543 ) );
        assertSerialization( "\"" + time.toString() + "\"", time );
    }
}