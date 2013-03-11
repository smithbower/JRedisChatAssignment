import java.util.Hashtable;
import java.util.Map;

import org.json.JSONObject;

import redis.clients.jedis.Jedis;


/**
 * Users spawn threads that subscribe to Redis channels and wait for incoming messages.
 * This keeps track of user state and whatnot.
 * Also implements command logic.
 * 
 * Commands are:
 * 		me			/me
 * 		join		/join
 * 		whois		/whois
 * 		tell		/tell
 * 		delete		/delete
 * 		leave		/leave
 *		sendMessage /chat 
 *
 *
 * Assignment notes:
 * When talking to Redis, we use the following format for channels: channel:name
 * 		i.e. The channel "redisrox" would be specified by "channel:redisrox".
 * 
 * Users are similar to the aforementioned channels, we use user:name.
 * 		i.e. "user:stephen".
 * 
 * this.conn is the Jedis object we use to talk to Redis.
 * 
 * this.channels is just used for housekeeping.
 * 
 * All messages are sent using JSON formatting. BUT DON'T WORRY - a JSON lib has
 * been included for you. JSON messages are built using JSONObject, which has the
 * same interface as Java Map (i.e. json.put("key", "value") ). To get the string
 * you must send to Redis on a publish, use json.toString().
 *
 *
 * @author Stephen Smithbower 2013
 */
public class User 
{
	//////////////////////////////////////////////////////
	// INSTANCE DATA									//
	//////////////////////////////////////////////////////
	/**
	 * The user's unique username.
	 */
	public String username;
	
	/**
	 * Host Redis IP address.
	 */
	public String host;
	
	/**
	 * User connection used to publish messages on. Messages broadcasted on all
	 * channels will use this connection. Separate connections are used per-channel.
	 */
	public Jedis conn;
	
	/**
	 * Tracks all channels we're subscribed to so we can close them up properly.
	 */
	public Hashtable<String, Channel> channels = new Hashtable<String, Channel>();
	
	/**
	 * Recipient of events when a message is received.
	 */
	private IMessageEventListener messageListener;
	
	
	
	
	//////////////////////////////////////////////////////
	// METHODS											//
	//////////////////////////////////////////////////////
	/**
	 * Creates a new User connection to Redis.
	 * 
	 * @param host The host Redis IP address.
	 * @param messageListener The recipient of events when a message is received.
	 */
	public User(String host, IMessageEventListener messageListener)
	{
		this.messageListener = messageListener;
		
		this.username = ""; //Blank, because we don't have a name yet. Should be set by whois.
		
		this.host = host;
		this.conn = new Jedis(host);
	}
	

	/**
	 * Has the user join a channel.
	 * 
	 * @param channel The name of the channel to join.
	 */
	public void join(String channel)
	{
		if (this.username.equals(""))
			return; //TODO: Throw exception.
		
		//Make sure we haven't already joined this channel.
		if (this.channels.containsKey("channel:" + channel))
			return; //TODO: Throw exception.
		
		//Update channel count.
		//////////////////
		// Assignment	//
		//////////////////
		//We want to add this channel (channel:channelname) to our user's list of channels
		//(channels:username). Remember that this is a SetADD... (hint hint).
		long channelCount = this.conn.
		
		//Start listening to the channel.
		this.channels.put("channel:" + channel, new Channel(this.host, "channel:" + channel, this.messageListener, this));
		
		//Notify users that we've joined the channel.
		if (channelCount > 0)
		{
			JSONObject json = new JSONObject();
			json.put("name", "SERVER");
			json.put("message", this.username + " has joined channel: " + channel);
			
			
			//////////////////
			// Assignment	//
			//////////////////
			//We need to PUBLISH the json message to the channel we just joined.
			//Remember the format, channel:channelname
		}
		
		//Refresh user expiration countdown.
		refreshUserExpiration();
	}
	
	
	/**
	 * Has the user identify itself to the server.
	 * 
	 * @param name The name of the user. If the user already has a different name,
	 * 			   and this name is available, we delete the old name and use this
	 * 			   one.
	 * @param age The age of the user.
	 * @param sex The sex of the user (male/female).
	 * @param location The user's location (ASL??)
	 */
	public void me(String name, int age, String sex, String location)
	{
		//Check to see if the user already exists.
		if (this.conn.exists("user:" + name))
			return;//TODO: Throw an exception "[me] Error: That user already exists!";
		
		//If we're renaming ourselves, delete the old name.
		if (!this.username.equals(""))
		{
			//////////////////
			// Assignment	//
			//////////////////
			//What command might we use to delete something?
			this.conn.METHOD_GOES_HERE("user:" + this.username);
			this.conn.METHOD_GOES_HERE("channels:" + this.username);
			
			//Stop listening on channel:all, and old username.
			Channel chanAll = this.channels.get("channel:all");
			Channel chanOldname = this.channels.get("channel:" + this.username);
			
			chanAll.stop();
			chanOldname.stop();
			
			this.channels.remove("channel:all");
			this.channels.remove("channel:" + this.username);
		}
		
		//Ok, add ourselves.
		this.username = name;
		Map<String, String> vals = new Hashtable<String, String>();
		vals.put("name", this.username);
		vals.put("age", Integer.toString(age));
		vals.put("sex", sex);
		vals.put("location", location);
		
		//////////////////
		// Assignment	//
		//////////////////
		//Command has something to do with a has set..
		this.conn.METHOD_GOES_HERE("user:" + this.username, vals);
		
		//Delete the user if they sit idle for 3 minutes.
		refreshUserExpiration();
		
		//Update channel listing.
		//////////////////
		// Assignment	//
		//////////////////
		//Need to do a set add..
		this.conn.METHOD_GOES_HERE("channels:" + this.username, "channel:" + this. username, "channel:all");
		
		
		//Subscribe to channels.
		this.channels.put("channel:all", new Channel(this.host, "channel:all", this.messageListener, this));
		this.channels.put("channel:" + this.username, new Channel(this.host, "channel:" + this.username, this.messageListener, this));
		
		
		//Notify everyone that we've joined the server.
		JSONObject json = new JSONObject();
		json.put("name", "SERVER");
		json.put("message", this.username + " has joined the server");
		
		//////////////////
		// Assignment	//
		//////////////////
		//Publishing time! Might want to use channel:all..
		this.conn.METHOD_GOES_HERE("channel:all", json.toString());
	}
	
	
	/**
	 * Gets some data on a given user.
	 * 
	 * @param user The name of the user to get data for.
	 * 
	 * @return A key-value pairing of data on the user.
	 */
	public Map<String, String> whois(String user)
	{
		if (this.username.equals(""))
			return null; //TODO: Throw exception.
		
		//Refresh user expiration countdown.
		refreshUserExpiration();
		
		//Get the Redis data structure.
		
		//////////////////
		// Assignment	//
		//////////////////
		//We want to get all values in a hash set..
		return this.conn.METHOD_GOES_HERE("user:" + user);
	}
	
	
	/**
	 * Has the user leave a channel.
	 * 
	 * @param channel The name of the channel to leave.
	 */
	public void leave(String channel)
	{
		if (this.username.equals(""))
			return; //TODO: Throw exception.
		
		//Make sure we've joined this channel.
		if (!this.channels.containsKey("channel:" + channel))
			return; //TODO: Throw exception.
		
		//Make sure the channel isn't empty.
		//////////////////
		// Assignment	//
		//////////////////
		//We want to do a set remove..
		long channelCount = this.conn.METHOD_GOES_HERE("channels:" + this.username, "channel:" + channel);
		
		if (channelCount > 0)
		{
			//Notify all users in the channel that we've left.
			JSONObject json = new JSONObject();
			json.put("name", "SERVER");
			json.put("message", this.username + " has left channel: " + channel);
			
			//////////////////
			// Assignment	//
			//////////////////
			//Publish json to the specified channel. Remember format, channel:channelname
			this.conn.
		}
		
		//Stop listening on this channel.
		Channel chanByeBye = this.channels.get("channel:" + channel);
		chanByeBye.stop();
		this.channels.remove("channel:" + channel);
		
		//Refresh user expiration countdown.
		refreshUserExpiration();
	}
	
	
	/**
	 * Broadcasts a message to a given channel.
	 * 
	 * @param channel The name of the channel to broadcast on. Defaults to all
	 * 				  if none is specified.
	 * @param message The message to send.
	 */
	public void sendMessage(String channel, String message)
	{
		if (this.username.equals(""))
			return; //TODO: Throw exception.
		
		if (channel.equals("")) //Default to "all"
			channel = "all";
		
		//Make sure we've joined this channel.
		if (!this.channels.containsKey("channel:" + channel))
			return; //TODO: Throw an exception.
		
		//Create the message.
		JSONObject json = new JSONObject();
		json.put("name", this.username);
		json.put("channel", channel);
		json.put("message", message);
		
		//Publish the message.
		//////////////////
		// Assignment	//
		//////////////////
		//Publish json to the specified channel. Remember format, channel:channelname
		this.conn.
		
		//Refresh user expiration countdown.
		refreshUserExpiration();
	}
	
	
	/**
	 * Drops the user off a cliff.
	 */
	public void delete()
	{
		if (this.username.equals(""))
			return; //TODO: Throw exception.
		
		this.conn.del("user:" + this.username);
		this.conn.del("channels:" + this.username);
		
		//Let everyone else on the server know that we've left.
		JSONObject json = new JSONObject();
		json.put("name", this.username);
		json.put("message", this.username + " has left the server");
		//////////////////
		// Assignment	//
		//////////////////
		//Publish json to all..
		this.conn.
		
		//Stop listening on all channels.
		for(String chan : this.channels.keySet())
			this.channels.get(chan).stop();
		
		this.channels.clear();
		
	}
	
	
	/**
	 * Sends a private message to a given user on their private channel.
	 * 
	 * @param user The name of the user to send the message to.
	 * @param message The message to send to the other user.
	 */
	public void tell(String user, String message)
	{
		if (this.username.equals(""))
			return; //TODO: Throw exception.
		
		//Create the message packet.
		JSONObject json = new JSONObject();
		json.put("name", this.username);
		json.put("channel", user);
		json.put("message", message);
		
		//Whisper the user on their private channel.
		//////////////////
		// Assignment	//
		//////////////////
		//Publish json to a specific user. Might think of channel:(what might go here?)..
		this.conn.
		
		//Refresh user expiration countdown.		
		refreshUserExpiration();
	}
	
	
	/**
	 * Resets user timeouts to 3 minutes, after which they will be kicked.
	 */
	private void refreshUserExpiration()
	{
		this.conn.expire("user:" + this.username, 60 * 3);
		this.conn.expire("channels:" + this.username, 60 * 3);
	}
}
