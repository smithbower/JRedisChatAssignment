import org.json.JSONObject;
import org.json.JSONTokener;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * Simple wrapper class to manage kicking off a separate thread per
 * Redis subscription (as listening on a channel is a blocking op).
 * 
 * This class makes an event callback when a message is received.
 * 
 * @author Stephen Smithbower 2013
 *
 */
public class Channel 
{
	//////////////////////////////////////////////////////
	// INSTANCE DATA									//
	//////////////////////////////////////////////////////
	/**
	 * Per-channel Jedis connection.
	 */
	private Jedis jedis;
	
	/**
	 * The channel we're listening on, in format "channel:name".
	 */
	private String channel;
	
	/**
	 * The thread we're using to listen on.
	 */
	private Thread listeningThread;
	
	/**
	 * Required because Java is kinda silly and makes up type a bunch
	 * of crap in order to get threading to work.
	 */
	private Client client;
	
	/**
	 * Host IP address.
	 */
	private String host;
	
	/**
	 * Parent user class (Users spawn channels) - need it so we can pass
	 * it back when a message is received, in case there are multiple
	 * Users on this client.
	 */
	private User user;
	
	/**
	 * Recipient of event raised when message is received.
	 */
	private IMessageEventListener messageListener;
	
	
	//////////////////////////////////////////////////////
	// METHODS											//
	//////////////////////////////////////////////////////
	/**
	 * Starts listening on a new Channel. Call stop() to stop listening and kill the thread.
	 * 
	 * @param host Redis IP address to connect to.
	 * @param channel The name of the channel to listen on.
	 * @param messageListener The recipient of events when a message is received.
	 * @param user The parent User that we're listening for.
	 */
	public Channel (String host, String channel, IMessageEventListener messageListener, User user)
	{
		this.jedis = new Jedis(host);
		this.channel = channel;
		this.host = host;
		this.messageListener = messageListener;
		this.user = user;
		
		this.client = new Client(host, channel, this.jedis, this.messageListener, user);
		this.listeningThread = new Thread(this.client);
		this.listeningThread.start();
	}
	
	
	/**
	 * Stops polling Redis and kills the thread.
	 */
	public void stop()
	{
		this.client.pubsub.unsubscribe();
	}
	
	
	/**
	 * Gets the Host IP address.
	 * @return
	 */
	public String getHost()
	{
		return this.host;
	}
	
	
	/**
	 * Gets the channel name.
	 * @return
	 */
	public String getChannel()
	{
		return this.channel;
	}
	
	
	
	//////////////////////////////////////////////////////
	// PRIVATE CLASSES									//
	//////////////////////////////////////////////////////
	
	/**
	 * This is just because Java is silly.
	 * 
	 * @author Stephen Smithbower 2013
	 *
	 */
	private class Client implements Runnable
	{
		private Jedis jedis;
		private String channel;
		public PubSub pubsub;
		private IMessageEventListener messageListener;
		private User user;
		
		public Client(String host, String channel, Jedis jedis, IMessageEventListener messageListener, User user)
		{
			this.channel = channel;
			this.jedis = jedis;
			this.user = user;
			this.messageListener = messageListener;
		}
		
		@Override
		public void run() 
		{
			try
			{
				this.pubsub = new PubSub(this.messageListener, this.user);
				jedis.subscribe(this.pubsub, this.channel);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	
	
	/**
	 * Extends JedisPubSub class in order to bubble-up messages.
	 * @author Stephen Smithbower 2013
	 *
	 */
	private class PubSub extends JedisPubSub
	{
		/**
		 * Recipient of events when a message is received.
		 */
		private IMessageEventListener messageListener;
		
		/**
		 * Parents User that we're listening for.
		 */
		private User user;
		
		public PubSub(IMessageEventListener messageListener, User user)
		{
			this.messageListener = messageListener;
			this.user = user;
		}
		
		@Override
		public void onMessage(String channel, String message)
		{
			//Decode the JSON message.
			JSONObject msg = new JSONObject(new JSONTokener(message));
			this.messageListener.onMessageReceived(this.user, msg.get("name").toString(), channel, msg.get("message").toString());
		}
		
		@Override
		public void onSubscribe(String channel, int numSubscribedChannels)
		{
			//System.out.println("onSubscribe: [" + channel + "] " + numSubscribedChannels);
		}
		
		@Override
		public void onUnsubscribe(String channel, int numSubscribedChannels)
		{
			//System.out.println("onUnsubscribe: [" + channel + "] " + numSubscribedChannels);
		}
		
		@Override
		public void onPMessage(String pattern, String channel, String message)
		{
			
		}
	
		@Override
		public void onPSubscribe(String pattern, int numSubscribedChannels) 
		{
			
		}
	
		@Override
		public void onPUnsubscribe(String pattern, int numSubscribedChannels) 
		{
			
		}
	}
}
