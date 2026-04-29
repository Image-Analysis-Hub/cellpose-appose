package fiji.plugin.appose.cellpose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import fiji.plugin.appose.ApposeUtils;

public class TestApposeUtils 
{

	@Test
	void test_convertChannelChoice() 
	{
		// CP3 and CP4 when None is selected -> Null
		assertNull( ApposeUtils.convertChannelChoiceToInt( "None", true ) );
		assertNull( ApposeUtils.convertChannelChoiceToInt( "None", false ) );
		// CP3 and CP4 when channel 1 is selected -> 1 for CP3, 0 for CP4
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", true ), 1 );
		assertEquals( ApposeUtils.convertChannelChoiceToInt( "1", false ), 0 );
				
	}

}
