import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;


/**
 * Silly little Swing UI for our chat demo.
 * 
 * @author Stephen Smithbower 2013
 */
public class ChatUI implements Runnable
{
	public JRedisChat client;
	private JTextArea messageList;
	
	public ChatUI(JRedisChat client)
	{
		this.client = client;
	}
	
	private class UserCommandListener extends KeyAdapter
	{
		public JRedisChat client;
		public JTextField textField;
		
		public UserCommandListener(JRedisChat client, JTextField textField)
		{
			this.client = client;
			this.textField = textField;
		}
		
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (e.getKeyChar() == KeyEvent.VK_ENTER)
			{
				this.client.handleUserCommand(this.textField.getText());
				this.textField.setText("");
			}
		}
	}
	
		
	@Override
	public void run()
	{
		//Create window.
		JFrame mainFrame = new JFrame("Redis PubSub Chat!");
			
		//Set default behavior to kill app when closing.
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		//Content panel.
		JPanel contentPanel = new JPanel();
		mainFrame.add(contentPanel);
			
		//Add a layout manager because I'm lazy.
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
			
		//Add main console.
		this.messageList = new JTextArea(40, 80);
		contentPanel.add(this.messageList);
			
		//Add command line.
		JTextField textField = new JTextField(80);
		UserCommandListener textListener = new UserCommandListener(this.client, textField);
		textField.addKeyListener(textListener);
		contentPanel.add(textField);
			
			
		//Flow layout and display.
		mainFrame.pack();
		mainFrame.setVisible(true);
	}
	
	public void addMessage(String message)
	{
		this.messageList.setText(this.messageList.getText() + "\r\n" + message);
	}
}