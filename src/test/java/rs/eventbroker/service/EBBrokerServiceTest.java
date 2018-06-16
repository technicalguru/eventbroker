/**
 * 
 */
package rs.eventbroker.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import com.mchange.util.AssertException;

import rs.eventbroker.AbstractServiceTest;
import rs.eventbroker.db.subscriber.ISubscriberBO;
import rs.eventbroker.db.subscriber.SubscriberDao;
import rs.eventbroker.rest.RestResult;

/**
 * Test of the {@link EBBrokerService}.
 * @author ralph
 *
 */
public class EBBrokerServiceTest extends AbstractServiceTest {

	/**
	 * Constructor.
	 */
	public EBBrokerServiceTest() {
		super(false);
	}

	/**
	 * Test the subscribe service call without authorization.
	 */
	@Test
	public void testSubscribeWithoutAuthorization() {
		testSubscribe(1, false);
	}
	
	/**
	 * Test the subscribe service call with authorization.
	 */
	@Test
	public void testSubscribeWithAuthorization() {
		testSubscribe(2, true);
	}
	
	/**
	 * Test the subscribe service call.
	 */
	public void testSubscribe(int version, boolean hasAuthorization) {
		// Create data
		SubscribeData data = createSubscribeData(version, hasAuthorization);
		Entity<SubscribeData> payload = Entity.entity(data, MediaType.APPLICATION_JSON_TYPE);
		RestResult<SubscribeResultData> response = (RestResult<SubscribeResultData>)getRequest("subscribe", null).post(payload, new GenericType<RestResult<SubscribeResultData>>() {});
		if (response.isSuccess()) {
			// Check that response has all information
			SubscribeResultData result = response.getData();
			assertEquals("packetId idoes not match", data.getPacketId(), result.getPacketId());
			List<Integer> rc = result.getReturnCodes();
			assertNotNull("response has no return codes", rc);
			assertEquals("invalid number of returnCodes", data.getTopics().size(), rc.size());
			for (Integer i : rc) {
				assertTrue("invalid return code", i.intValue() >= 0 && i.intValue() <= 2);
			}
			
			// Check that data is in database and remove it again
			getServiceFactory().begin();
			SubscriberDao dao = getServiceFactory().getSubscriberDao();
			for (String topic : data.getTopics()) {
				ISubscriberBO bo = dao.findBy(topic, data.getCallbackUrl());
				assertNotNull("Subscription for "+topic+" was not persisted", bo);
				if (hasAuthorization) {
					assertNotNull("Subscription shall have authorization", bo.getAuthorization());
				} else {
					assertNull("Subscription shall not have authorization", bo.getAuthorization());
				}
				// Remove it again
				dao.delete(bo);
			}
			getServiceFactory().commit();
		} else {
			throw new AssertException("subscribe call was not successful: "+response.getErrorMessage());
		}
	}
	
	/**
	 * Test the unsubscribe service call.
	 */
	@Test
	public void testUnsubscribe() {
		// Create data in DB
		getServiceFactory().begin();
		SubscriberDao dao = getServiceFactory().getSubscriberDao();
		List<String> topics = createTopics(3);
		for (String topic : topics) {
			ISubscriberBO bo  = dao.newInstance(); 
			bo.setTopic(topic);
			bo.setUrl("http://localhost/"+3);
			bo.setAuthorization(null);
			dao.create(bo);
		}
		
		UnsubscribeData data = createUnsubscribeData(3);
		Entity<UnsubscribeData> payload = Entity.entity(data, MediaType.APPLICATION_JSON_TYPE);
		RestResult<UnsubscribeResultData> response = (RestResult<UnsubscribeResultData>)getRequest("unsubscribe", null).post(payload, new GenericType<RestResult<UnsubscribeResultData>>() {});
		if (response.isSuccess()) {
			// Check that response has all information
			UnsubscribeResultData result = response.getData();
			assertEquals("packetId idoes not match", data.getPacketId(), result.getPacketId());
			
			// Check that data is removed from database
			for (String topic :topics) {
				ISubscriberBO bo = dao.findBy(topic, data.getCallbackUrl());
				assertNull("Subscription for "+topic+" is still persisted", bo);
			}
		} else {
			throw new AssertException("unsubscribe call was not successful: "+response.getErrorMessage());
		}
		getServiceFactory().commit();
	}
	
	private static SubscribeData createSubscribeData(int version, boolean hasAuth) {
		SubscribeData data = new SubscribeData();
		data.setPacketId(StringUtils.leftPad(""+version, 10, '0'));
		data.setTopics(createTopics(version));
		data.setCallbackUrl("http://localhost/"+version);
		if (hasAuth) data.setAuthorization("authorization"+version);
		return data;
	}
	
	
	private static UnsubscribeData createUnsubscribeData(int version) {
		UnsubscribeData data = new UnsubscribeData();
		data.setPacketId(StringUtils.leftPad(""+version, 10, '0'));
		data.setTopics(createTopics(version));
		return data;
	}
	
	private static List<String> createTopics(int version) {
		String t[] = createSingleTopics(version);
		List<String> rc = new ArrayList<>();
		rc.add(t[0]+'/'+t[1]+'/'+t[2]+"/+/"+t[3]);
		rc.add(t[4]+'/'+t[5]+"/*/"+t[6]);
		rc.add(t[7]+'/'+t[8]+'/'+t[9]+'/'+t[10]+'/'+t[11]);
		return rc;
	}
	
	private static String[] createSingleTopics(int version) {
		String rc[] = new String[100];
		int baseVersion = version * rc.length * 10;
		for (int i=0; i<rc.length; i++) {
			rc[i] = StringUtils.leftPad(""+(baseVersion+i), 10, '0');
		}
		return rc;
	}
}
