package mainPackage;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;

public class Server{
	
	private ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	private ArrayList<Room> rooms = new ArrayList<Room>();
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
	
	private synchronized void broadcastToRoom(String message, String roomName){
		message+='\n';
		//first, find the room corresponding to roomName
		for(Room room:rooms){
			if(room.getRoomName().compareTo(roomName)==0){
				//then go through each client in the room and send him a message
				//if client cant receive the message, then disconnect him from room.
				for(int i = room.roomClients.size()-1; i >= 0; i--){
					ClientThread client = room.roomClients.get(i);
					if(!client.writeMsg(message)){
						remove(i, room);
						System.out.println("Disconnected Client " + client.username +
								" from room " + room.getRoomName());
					}
				}
				return;
			}
		}
	}

	synchronized void remove(int position, Room room){
		room.roomClients.remove(position);
		return;
	}
	
	//this gets called by broadcast() when a client sends in a "/LOGOUT" request
	synchronized void remove(int position) {
				clients.remove(position);
				return;
	}

	//the client thread will be run for every client connected to the server
	private class ClientThread extends Thread {
		// the socket where to listen/talk
		Socket socket;
		BufferedWriter sOutput;
		BufferedReader sInput;
		// the username and password of the client
		String username;
		String password;
		String roomName;
		// the only type of message a will receive
		String message;
		//this boolean determines whether the client thread keeps running or not
		boolean keepGoing = true, needToLogIn = true, needToSelectRoom = true;
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
			// to loop until LOGOUT
			while(keepGoing) {
				try {
					message = sInput.readLine();
				}
				catch (IOException e) {
					break;				
				}
				//If the message is a server command (starts with "/"), then process the command
				if(message.charAt(0)=='/'){
					try {
						processCommand(message);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				//Otherwise, if this thread is logged in and its room is selected, let it send a message.
				else if(!needToLogIn && !needToSelectRoom){
					broadcastToRoom(username + ": " + message, roomName);
				}
				//Otherwise, tell the room that it cannot send the message.
				else writeMsg("CANT_SEND_MESSAGE\n");
				//KEEP IN MIND THAT BROADCAST IS SYNCHRONIZED, CHECK HOW THIS WORKS WITH MANY USERS
				//broadcast(username + ": " + message + "\n");	
				//to prevent threads from eating up all the processing time, 
				//the thread will run every second
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
			System.out.println("CLOSE WAS CALLED!!");
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
		
		void processCommand(String msg) throws IOException{
			String[] command = msg.split(" ");
			//If thread logged out, tell the room that client DC'd.
			//Note: this will end up closing the connection of the username thread.
			if(command.length==1 && command[0].compareTo("/LOGOUT")==0 && !needToLogIn && !needToSelectRoom){
				broadcastToRoom(username + " has disconnected.", roomName);
			}
			//If thread asked for a disconnect, close it.
			if(command.length==1 && command[0].compareTo("/DISCONNECT")==0){
				//If the client invoked the disconnect command while logged in and in a room, just log him out.
				if(!needToLogIn && !needToSelectRoom){
					processCommand("/LOGOUT");
				}
				//Otherwise, just close the connection.
				close();
			}
			//If the command was a login or registration request, process the login data.
			if(command.length == 3 && (command[0].compareTo("/LOGIN")==0 || command[0].compareTo("/REGISTER")==0)){
				try {
					processLoginData(command);
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			//If the command is a join room request:
			if(command.length == 2 && (command[0].compareTo("/JOIN_ROOM")==0)){
				selectRoom(command[1], false);
			}
			//If the command is a create room request:
			if(command.length==2 && (command[0].compareTo("/CREATE_ROOM")==0)){
				selectRoom(command[1], true);
			}
			//If the command is to request the list of rooms:
			//Note: check whether the predicate string needs an endline.
			if(command.length==1 && (command[0].compareTo("/REQUEST_ROOMS")==0)){
				//create string of all the room names on the server
				String roomData = "";
				for(Room r:rooms){
					roomData+=r.getRoomName();
				}
				roomData+='\n';
				writeMsg(roomData);
			}
		}


		private boolean writeMsg(String msg) {
			//if socket isnt connected, close down the client thread
			if(!socket.isConnected()) {
				System.out.println("SOCKET WAS DISCONNECTED");
				close();
				return false;
			}
			if(msg.compareTo(username + ": " + " has disconnected.\n")==0){
				System.out.println("CLIENT LOGGED OUT");
				close();
				return false;
			}
			System.out.println("Writing out a message: " + msg);
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
		
		private synchronized void processLoginData(String[] loginInfo) throws SQLException, IOException{
			//check if the login data array is of the form "REGISTER username password"
			if(loginInfo[0].compareTo("/REGISTER")==0){
				MYSQL_ACCESS_TYPE resultOfRegistration =
						dbAccessor.readDataBase(MYSQL_ACCESS_TYPE.REGISTER, loginInfo[1], loginInfo[2]);
				switch(resultOfRegistration){
				case REGISTER_ERROR_USERNAME_ALREADY_EXISTS:
					writeMsg("REGISTER_ERROR_USERNAME_ALREADY_EXISTS\n");
					break;
				case REGISTER_SUCCESS:
					writeMsg("REGISTER_SUCCESS\n");
					username = loginInfo[1];
					password = loginInfo[2];
					needToLogIn = false;
					break;
				default:
					writeMsg("REGISTER_UNKNOWN_ERROR\n");
					this.close();
				}
			}
			//check if the login information is a login request
			else if(loginInfo[0].compareTo("/LOGIN")==0){
				MYSQL_ACCESS_TYPE resultOfLogin = 
						dbAccessor.readDataBase(MYSQL_ACCESS_TYPE.LOGIN, loginInfo[1], loginInfo[2]);
				switch(resultOfLogin){
				case LOGIN_SUCCESS:
					username = loginInfo[1];
					password = loginInfo[2];
					writeMsg("LOGIN_SUCCESS\n");
					System.out.println("needToLogIn SET TO FALSE");
					needToLogIn = false;
					break;
				case LOGIN_ERROR_USERNAME_NOT_FOUND:
					writeMsg("LOGIN_ERROR_USERNAME_NOT_FOUND\n");
					break;
				case LOGIN_ERROR_INCORRECT_PASSWORD:
					writeMsg("LOGIN_ERROR_INCORRECT_PASSWORD\n");
					break;
				default:
					writeMsg("LOGIN_UNKNOWN_ERROR\n");
					this.close();
				}
			}
			
		}

		//this function will run once the user has logged into the client and is picking a room to join
		private void selectRoom(String name, boolean createNew) throws IOException{
			//if the user decided to create a new room
			if(createNew){
				boolean alreadyExists = false;
				//check if that room name already exists
				for(Room r:rooms){
					if(r.getRoomName().compareTo(name)==0){
						alreadyExists = true;
						break;
					}
				}
				//if it does, give error to user
				if(alreadyExists){
					writeMsg("ROOM_ALREADY_EXISTS\n");
				}
				//otherwise, create the room and put the client thread into it
				else{
					Room r = new Room(name);
					rooms.add(r);
					r.roomClients.add(this);
					roomName = r.getRoomName();
					needToSelectRoom=false;
					writeMsg("ROOM_JOINED\n");
				}
			}
			//Otherwise, the user is trying to join the room, so...
			else{
				//check if room exists
				Room room = null;
				for(Room r:rooms){
					if(r.getRoomName().compareTo(name)==0){
						room=r;
					}
				}
				//place client into room
				if(room != null){
					room.roomClients.add(this);
					roomName = room.getRoomName();
					needToSelectRoom=false;
					writeMsg("ROOM_JOINED\n");
				}else{
					writeMsg("ROOM_DOESNT_EXIST\n");
				}
			}
		}
	}
	
	private class Room extends Thread{
		private String name;
		ArrayList<ClientThread> roomClients = new ArrayList<ClientThread>();
		
		Room(String name){
			this.name = name;
		}
		
		
		public String getRoomName(){
			return name;
		}
		
		private void close(){
			for(ClientThread t:roomClients){
				t.writeMsg("This room has been closed. Disconnecting from the server.\n");
				t.close();
			}
		}
		
		
	}
	
	public static void main(String[] args){
		// create a server object
		Server server = new Server();
	}
}



