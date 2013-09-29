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
    //chatUpdateTimer->blockSignals(true);
    UPDATE_CHAT_TIME = 200;
    //chatUpdateTimer->start(UPDATE_CHAT_TIME);

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
    if(clientSocket->waitForConnected(1000)){
        QString clientStreamString = clientSocket->readAll();
        if(clientStreamString != ""){
            ui->chatText->insertPlainText(QString(clientStreamString));
            ui->chatText->verticalScrollBar()->setSliderPosition(
                ui->chatText->verticalScrollBar()->maximum());
        }
    }
    chatUpdateTimer->start();
}

void MainWindow::on_logInButton_clicked()
{
    //write username and password to the server
    QString loginInfoQStr = ui->usernameInput->text() + " " + ui->passwordInput->text();
    if(loginInfoQStr.count(" ") != 1 || ui->usernameInput->text() == "" || ui->passwordInput->text() == ""){
        ui->serverMessageLogIn->setText("You have entered an invalid username or password!");
        return;
    }

    const char* loginInfo = (loginInfoQStr+"\n").toUtf8().constData();
    clientSocket->write(loginInfo);

    if(clientSocket->waitForConnected(10000)){
        QString clientLogInResponse = clientSocket->readAll();
        switch(clientLogInResponse){
        case "LOGIN_SUCCESS\n":
            ui->stackedWidget->setCurrentWidget(ui->chatPage);
            //NEED TO CHECK THE MESSAGE
            break;
        default:
            ui->serverMessageLogIn->setText("Recieved an unknown response from the server. Please try again.");
        }
    }

    //DO ACCESS CHECKING HERE

    //ui->stackedWidget->setCurrentWidget(ui->chatPage);
}

void MainWindow::on_registerButton_clicked()
{
    QString registerInfoQStr = "REGISTER " + ui->usernameInput->text() +  " " + ui->passwordInput->text();
    if(registerInfoQStr.count(" ") != 2 || ui->usernameInput->text() == "" || ui->passwordInput->text() == ""){
        ui->serverMessageLogIn->setText("You have entered an invalid username or password!");
        return;
    }
    ui->serverMessageLogIn->setText("Sending your username to server...");
    const char* registerInfo = (registerInfoQStr+"\n").toUtf8().constData();
    clientSocket->write(registerInfo);
}

void MainWindow::closeEvent(QCloseEvent *event)
{
    logout(CLOSE_LOGOUT);
    event->accept();
}

void MainWindow::logout(LOGOUT_TYPE t){
    clientSocket->write(QString("/LOGOUT").toUtf8().constData());
    if(t == BUTTON_LOGOUT || t == MESSAGE_LOGOUT) this->close();
}
