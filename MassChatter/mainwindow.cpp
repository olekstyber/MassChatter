#include "mainwindow.h"

MainWindow::MainWindow(QWidget *parent) :
    QMainWindow(parent),
    ui(new Ui::MainWindow)
{
    ui->setupUi(this);
    ui->stackedWidget->setCurrentWidget(ui->logInPage);

    //for the login fields
    //set echo mode of password input to password for security
    ui->passwordInput->setEchoMode(QLineEdit::Password);

    //for the chat window
    //set up the event filter onto usertextinput so it can catch a return input
    ui->userTextInput->installEventFilter(this);
    //set up the readonly property of chatText
    ui->chatText->setReadOnly(true);

    IP = "localhost";
    PORT = 5999;

    clientSocket = new QTcpSocket(); //create TCP-based socket
    clientSocket->connectToHost(IP,PORT);

    chatUpdateTimer = new QTimer(this);
    connect(chatUpdateTimer, SIGNAL(timeout()), this, SLOT(updateChat()));
    UPDATE_CHAT_TIME = 200;
}

MainWindow::~MainWindow()
{
    delete ui;
    delete clientSocket;
    delete chatUpdateTimer;
}

//this function is used to submit a message from the client to the server
//after a user presses enter, the program will check whether there is a message in user's text, and if there is, then it will
//send that message, in chars, to the server to redistribute to other clients
bool MainWindow::eventFilter(QObject *obj, QEvent *e){
    if(obj==ui->userTextInput && e->type() == QEvent::KeyPress &&
            static_cast<QKeyEvent*>(e)->key() == Qt::Key_Return){

        QString userInput = ui->userTextInput->toPlainText();

        if(userInput == ""){
            return true;
        }

        const char* userMsgInChars = (userInput+"\n").toUtf8().constData();
        clientSocket->write(userMsgInChars);

        if(userInput.compare("/LOGOUT")==0){
            logout(MESSAGE_LOGOUT);
        }

        ui->userTextInput->setPlainText("");

        return true;
    }

    return false;
}

//this function executes every UPDATE_CHAT_TIME ms and displays the data that was sent to the client by the server
void MainWindow::updateChat(){
    QString clientStreamString = recieveDataFromServer(true);
    if(clientStreamString != ""){
        ui->chatText->moveCursor(QTextCursor::End);
        ui->chatText->insertPlainText(QString(clientStreamString));
        ui->chatText->verticalScrollBar()->setSliderPosition(
            ui->chatText->verticalScrollBar()->maximum());
    }
    chatUpdateTimer->start();
}

//Checks whether the user input is acceptable as username/password and then tries to login the account.
void MainWindow::on_logInButton_clicked()
{
    //Check validity.
    QString loginInfoQStr = "/LOGIN " + ui->usernameInput->text() + " " + ui->passwordInput->text() + "\n";
    if(loginInfoQStr.count(" ") != 2 || ui->usernameInput->text() == "" || ui->passwordInput->text() == ""){
        ui->serverMessageLogIn->setText("You have entered an invalid username or password!");
        return;
    }

    //Request server's approval...
    ui->serverMessageLogIn->setText("Sending your username to server...");
    writeDataToServer(loginInfoQStr);

    QString clientLogInResponse = recieveDataFromServer(false);
    if(clientLogInResponse.compare("LOGIN_SUCCESS\n")==0){
        //On successful login, send user to roomSelectPage and update its contents.
        ui->stackedWidget->setCurrentWidget(ui->roomSelectPage);
        updateRoomSelectContents();
    }else if(clientLogInResponse.compare("LOGIN_ERROR_USERNAME_NOT_FOUND\n")==0){
        ui->serverMessageLogIn->setText("The username you entered does not exist.");
    }
    else if(clientLogInResponse.compare("LOGIN_ERROR_INCORRECT_PASSWORD\n")==0){
        ui->serverMessageLogIn->setText("The password you entered is incorrect.");
    }
    else{
        ui->serverMessageLogIn->setText("Recieved an unknown response from the server. Please try again.");
    }

}

//Checks whether the user input is acceptable as username/password and then tries to register the new account.
void MainWindow::on_registerButton_clicked()
{
    //Validity check.
    QString registerInfoQStr = "/REGISTER " + ui->usernameInput->text() +  " " + ui->passwordInput->text() + "\n";
    if(registerInfoQStr.count(" ") != 2 || ui->usernameInput->text() == "" || ui->passwordInput->text() == ""){
        ui->serverMessageLogIn->setText("You have entered an invalid username or password!");
        return;
    }
    //Request server's approval...
    ui->serverMessageLogIn->setText("Sending your username to server...");
    writeDataToServer(registerInfoQStr);

    //Interpret the resulting message.
    QString clientRegisterResponse = recieveDataFromServer(false);
    if(clientRegisterResponse.compare("REGISTER_SUCCESS\n")==0){
        //If new account was registered, send the user to roomSelect page.
        ui->stackedWidget->setCurrentWidget(ui->roomSelectPage);
        updateRoomSelectContents();
    }else if(clientRegisterResponse.compare("REGISTER_ERROR_USERNAME_ALREADY_EXISTS\n")==0){
        //Otherwise inform the user of the problem with the registration process.
        ui->serverMessageLogIn->setText("The username you are trying to register already exists.");
    }else{
        ui->serverMessageLogIn->setText("Recieved an unknown response from the server. Please try again.");
    }

}

//Close event catcher that tries to inform the server that the client logged out.
void MainWindow::closeEvent(QCloseEvent *event)
{
    logout(CLOSE_LOGOUT);
    event->accept();
}

//General logout function that also attempts to inform the server about the logout of this client.
void MainWindow::logout(LOGOUT_TYPE t){
    writeDataToServer("/LOGOUT\n");
    if(t == BUTTON_LOGOUT || t == MESSAGE_LOGOUT) this->close();
}

//Send a request to the server for rooms and distribute the returned data
//into the roomList.
void MainWindow::updateRoomSelectContents(){
    //request room data from the server
    writeDataToServer("/REQUEST_ROOMS\n");
    //wait for data to be transferred from server to client
    QString serverResponse = recieveDataFromServer(false);
    QStringList roomsList = serverResponse.split(" ");
    for(int i = 0; i<roomsList.size(); i++){
        ui->roomList->addItem(roomsList.at(i));
    }
}

//Clear the current roomList and update it.
void MainWindow::on_refreshRoomsButton_clicked()
{
    ui->roomList->clear();
    updateRoomSelectContents();
}

//Direct the user to newRoom creation window when he clicks the button.
void MainWindow::on_createNewRoomButton_clicked()
{
    ui->stackedWidget->setCurrentWidget(ui->createNewRoomPage);
}

//If the createRoom button was clicked, then send the roomName over to the server,
//try to recieve the reply, and interpret the input.
void MainWindow::on_createRoomButton_clicked()
{
    writeDataToServer("/CREATE_ROOM " + ui->newRoomNameInput->text() + "\n");
    QString serverResponse = recieveDataFromServer(false);

    if(serverResponse.compare("ROOM_ALREADY_EXISTS\n")==0){
        //Tell the user that the room already exists.
        ui->createRoomResponse->setText("The room with this name already exists.");
    }
    else if(serverResponse.compare("ROOM_JOINED\n")==0){
        //Place the user into the room and start the chat update timer.
        ui->stackedWidget->setCurrentWidget(ui->chatPage);
        chatUpdateTimer->start(UPDATE_CHAT_TIME);

    }
    else ui->createRoomResponse->setText("Unknown response from the server. Try again.");

}

//Holds for a max of 20 seconds trying to connect and read the data form the server.
//Returns a QString representation of the message that was recieved from the server.
//If the data couldnt be read or the socket couldnt get connected, then this returns an empty string.
//IgnoreReadyRead flag tells the function whether to wait until there is a message, potentially extending the hang time,
//or just return the empty string if there is no message.
QString MainWindow::recieveDataFromServer(bool ignoreReadyRead){
    QString out("");
    if(clientSocket->waitForConnected(10000) && (ignoreReadyRead || clientSocket->waitForReadyRead(10000))){
        out = clientSocket->readAll();
    }
    //For debugging purposes.
    qDebug() << out;
    return out;
}

//Given a QString data, convert it to proper format and send it to server for processing.
void MainWindow::writeDataToServer(QString data){
    clientSocket->write(data.toUtf8().constData());
}

//This function, after checking whether a room is selected, attempts to place the client into it.
void MainWindow::on_joinRoomButton_clicked()
{
    QListWidgetItem *selectedRoom = ui->roomList->currentItem();
    if(selectedRoom == NULL) return;
    QString selectedRoomName("/JOIN_ROOM " + selectedRoom->text() + "\n");
    //Send a request to join the room.
    writeDataToServer(selectedRoomName);
    QString serverResponse = recieveDataFromServer(false);
    if(serverResponse.compare("ROOM_JOINED\n")==0){
        //On successful join, let user get into chat and start the updates.
        ui->stackedWidget->setCurrentWidget(ui->chatPage);
        chatUpdateTimer->start(UPDATE_CHAT_TIME);
    }else if(serverResponse.compare("ROOM_DOESNT_EXIST\n")==0){
        //If somehow the user selected a room that got deleted in between refreshes, reload the room list.
        ui->roomList->clear();
        updateRoomSelectContents();
    }
}
