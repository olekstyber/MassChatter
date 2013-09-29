package mainPackage;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;

public class Server{
	
	private ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	private static final int PORT = 5999;
	private boolean serverRun;
	private ServerSocket serverSocket;
	
	private File accountInfo = new File("userData//acctInfo.data");
	PrintWriter fileOut;
	Scanner fileIn;
		
	public Server() {
		this.start();
	}
	
	
	public void start() {
		serverRun = true;
				
		try {
			loadBuffers();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
				
		try 
		{
			serverSocket = new ServerSocket(PORT);

			while(serverRun) 
			{
				System.out.println("Server waiting for Clients on port " + PORT + ".");
				
				Socket socket = serverSocket.accept();  	// accept connection
				System.out.println("Connection accepted! The new size of clients is " + clients.size());
				// if I was asked to stop
				if(!serverRun)
					break;
				ClientThread client = new ClientThread(socket);  // make a thread of it
				clients.add(client);									// save it in the ArrayList
				client.start();
			}
			// I was asked to stop
			try {
				serverSocket.close();
				for(int i = 0; i < clients.size(); ++i) {
					ClientThread clientToDisconnect = clients.get(i);
					try {
					clientToDisconnect.sInput.close();
					clientToDisconnect.sOutput.close();
					clientToDisconnect.socket.close();
					}
					catch(IOException ioE) {}
				}
			}
			catch(Exception e) {}
		}
		// something went bad
		catch (IOException e) {System.out.println(e.toString());} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}		

	protected void stop() throws IOException {
		serverRun = false;
	}

	
	private synchronized void broadcast(String message) {
		
		for(int i = clients.size(); --i >= 0;) {
			ClientThread client = clients.get(i);
			if(!client.writeMsg(message)) {
				clients.remove(i);
				System.out.println("Disconnected Client " + client.username + " removed from list.");
			}
		}
	}

	// for a client who logoff using the LOGOUT message
	synchronized void remove(int position) {
				clients.remove(position);
				return;
	}
	
	private void loadBuffers() throws IOException{
		fileIn = new Scanner(accountInfo);
		fileOut = new PrintWriter(new BufferedWriter(new FileWriter(accountInfo, true)));
	}
	
	
	public static void main(String[] args){
		// create a server object
		Server server = new Server();
	}

	//the client thread will be run for every client connected to the server
	class ClientThread extends Thread {
		// the socket where to listen/talk
		Socket socket;
		BufferedWriter sOutput;
		BufferedReader sInput;
		// the username and password of the client
		String loginInfo;
		String username;
		String password;
		// the only type of message a will receive
		String message;

		MySQLAccess a = new MySQLAccess();

		ClientThread(Socket socket) throws SQLException {
			
			this.socket = socket;

			try
			{	
				// create socket input/output streams
				sOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				sInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				// read the username and password
				loginInfo = sInput.readLine();
				String[] login = loginInfo.split(" ");
				processLoginData(login);
			}
			catch (IOException e) {
				return;
			}
		}

		public void run() {
			// to loop until LOGOUT
			boolean keepGoing = true;
			while(keepGoing) {
				try {
					message = sInput.readLine();
				}
				catch (IOException e) {
					break;				
				}
				//KEEP IN MIND THAT BROADCAST IS SYNCHRONIZED, CHECK HOW THIS WORKS WITH MANY USERS
				broadcast(username + ": " + message + "\n");	
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			close();
		}
		
		// try to close everything
		private void close() {
			fileIn.close();
			fileOut.close();
			// try to close the connection
			try {
				if(sOutput != null) sOutput.close();
			}
			catch(Exception e) {}
			try {
				if(sInput != null) sInput.close();
			}
			catch(Exception e) {};
			try {
				if(socket != null) socket.close();
			}
			catch (Exception e) {}
		}


		private boolean writeMsg(String msg) {
			// if Client is still connected send the message to it
			if(!socket.isConnected()) {
				close();
				return false;
			}
			if(msg.compareTo(username + ": " + "/LOGOUT" + "\n")==0){
				System.out.println("CLIENT LOGGED OUT");
				close();
				return false;
			}
			// write the message to the stream
			try {
				char[] chrArr = msg.toCharArray();
				sOutput.write(chrArr, 0, chrArr.length);
				sOutput.flush();
			}
			// if an error occurs, do not abort just inform the user
			catch(IOException e) {
				System.out.println("Error sending message to " + username);
				System.out.println(e.toString());
			}
			return true;
		}
		
		private void processLoginData(String[] login) throws SQLException{
			//check if the user terminated the client
			if(login.length == 1 && login[0].compareTo("/LOGOUT")==0) close();
			//check if the login data array is of the form "REGISTER username password"
			else if(login.length == 3 && login[0].compareTo("REGISTER")==0){
				a.readDataBase(MYSQL_ACCESS_TYPE.REGISTER, login[1], login[2]);
			}
			
		}
	}
}



