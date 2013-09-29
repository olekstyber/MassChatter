package mainPackage;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;

public class Server{
	
	private ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	//port of the server
	private static final int PORT = 5999;
	//boolean that determines whether the server should continue running
	private boolean serverRun;
	//server socket for this server
	private ServerSocket serverSocket;
		
	public Server() {
		this.start();
	}
	
	
	public void start() {
		serverRun = true;
				
		try 
		{
			serverSocket = new ServerSocket(PORT);

			while(serverRun) 
			{
				System.out.println("Server waiting for Clients on port " + PORT + ".");
				
				//accept connections
				Socket socket = serverSocket.accept();  
				System.out.println("Connection accepted! The new size of clients is " + clients.size());
				//in case server was stopped
				if(!serverRun)
					break;
				//if it accepts a new connection, then create a thread for it
				ClientThread client = new ClientThread(socket);
				clients.add(client);
				client.start();
			}
			//Server stopped running -- do clean up
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
		//Some error happened
		catch (IOException e) {System.out.println(e.toString());} catch (SQLException e) {
			e.printStackTrace();
		}
	}		

	//simply stop the server
	protected void stop() throws IOException {
		serverRun = false;
	}

	//(this will change in the future)
	//broadcast to all the users within the server
	private synchronized void broadcast(String message) {
		
		for(int i = clients.size(); --i >= 0;) {
			ClientThread client = clients.get(i);
			if(!client.writeMsg(message)) {
				clients.remove(i);
				System.out.println("Disconnected Client " + client.username + " removed from list.");
			}
		}
	}

	//this gets called by broadcast() when a client sends in a "/LOGOUT" request
	synchronized void remove(int position) {
				clients.remove(position);
				return;
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
		//this boolean determines whether the client thread keeps running or not
		boolean keepGoing = true, needToLogIn = true;
		//every client thread has its own access point to the server's 
		//database of usernames and passwords
		MySQLAccess dbAccessor = new MySQLAccess();

		ClientThread(Socket socket) throws SQLException {
			
			this.socket = socket;

			try
			{	
				// create socket input/output streams
				sOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				sInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			}
			catch (IOException e) {
				return;
			}
		}
		
		//the thread will keep running until it receives a "/LOGOUT" request
		//in a run, it will get a message from the client and broadcast() it to the server
		public void run() {
			if(needToLogIn){
				//keep trying to receive login data from the client
				try {
					processLoginData();
				} catch (SQLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else{
				// to loop until LOGOUT
				while(keepGoing) {
					try {
						message = sInput.readLine();
					}
					catch (IOException e) {
						break;				
					}
					//KEEP IN MIND THAT BROADCAST IS SYNCHRONIZED, CHECK HOW THIS WORKS WITH MANY USERS
					broadcast(username + ": " + message + "\n");	
					//to prevent threads from eating up all the processing time, 
					//the thread will run() every second
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			close();
		}
		
		// try to close everything
		private void close() {
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
			//if socket isnt connected, close down the client thread
			if(!socket.isConnected()) {
				close();
				return false;
			}
			if(msg.compareTo(username + ": " + "/LOGOUT" + "\n")==0){
				System.out.println("CLIENT LOGGED OUT");
				close();
				return false;
			}
			//write the message to the client of this thread
			try {
				char[] chrArr = msg.toCharArray();
				sOutput.write(chrArr, 0, chrArr.length);
				sOutput.flush();
			}
			catch(IOException e) {
				System.out.println("Error sending message to " + username);
				System.out.println(e.toString());
			}
			return true;
		}
		
		private void processLoginData() throws SQLException, IOException{
			//get data from the socket
			loginInfo = sInput.readLine();
			String[] login = loginInfo.split(" ");
			
			//check if the user terminated the client
			if(login.length == 1 && login[0].compareTo("/LOGOUT")==0) close();
			//check if the login data array is of the form "REGISTER username password"
			else if(login.length == 3 && login[0].compareTo("REGISTER")==0){
				MYSQL_ACCESS_TYPE resultOfRegistration =
						dbAccessor.readDataBase(MYSQL_ACCESS_TYPE.REGISTER, login[1], login[2]);
				switch(resultOfRegistration){
				case REGISTER_ERROR_USERNAME_ALREADY_EXISTS:
					writeMsg("REGISTER_ERROR_USERNAME_ALREADY_EXISTS\n");
					processLoginData();
					break;
				case REGISTER_SUCCESS:
					username = login[1];
					password = login[2];
					needToLogIn = false;
					break;
				default:
					writeMsg("REGISTER_UNKNOWN_ERROR\n");
					this.close();
				}
			}
			//check if the login data array is of the standard "username password" form
			else if(login.length == 2){
				MYSQL_ACCESS_TYPE resultOfLogin = 
						dbAccessor.readDataBase(MYSQL_ACCESS_TYPE.LOGIN, login[0], login[1]);
				switch(resultOfLogin){
				case LOGIN_SUCCESS:
					username = login[0];
					password = login[1];
					writeMsg("LOGIN_SUCCESS\n");
					needToLogIn = false;
					break;
				case LOGIN_ERROR_USERNAME_NOT_FOUND:
					writeMsg("LOGIN_ERROR_USERNAME_NOT_FOUND\n");
					processLoginData();
					break;
				case LOGIN_ERROR_INCORRECT_PASSWORD:
					writeMsg("LOGIN_ERROR_INCORRECT_PASSWORD\n");
					processLoginData();
					break;
				default:
					writeMsg("LOGIN_UNKNOWN_ERROR\n");
					this.close();
				}
			}
			
		}
	}
}



