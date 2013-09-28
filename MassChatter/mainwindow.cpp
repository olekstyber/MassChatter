#include "mainwindow.h"

MainWindow::MainWindow(QWidget *parent) :
    QMainWindow(parent),
    ui(new Ui::MainWindow)
{
    ui->setupUi(this);

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
    clientSocket->waitForConnected();

    chatUpdateTimer = new QTimer(this);
    connect(chatUpdateTimer, SIGNAL(timeout()), this, SLOT(updateChat()));
    UPDATE_CHAT_TIME = 200;
    chatUpdateTimer->start(UPDATE_CHAT_TIME);

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
        if(ui->userTextInput->toPlainText() == ""){
            return true;
        }

        const char* userMsgInChars = (ui->userTextInput->toPlainText()+"\n").toUtf8().constData();
        clientSocket->write(userMsgInChars);

        ui->userTextInput->setPlainText("");
        return true;
    }

    return false;
}

//this function executes every UPDATE_CHAT_TIME ms and displays the data that was sent to the client by the server
void MainWindow::updateChat(){

    QString clientStreamString = clientSocket->readAll();
    if(clientStreamString != ""){
        ui->chatText->insertPlainText(QString(clientStreamString));
        ui->chatText->verticalScrollBar()->setSliderPosition(
            ui->chatText->verticalScrollBar()->maximum());
    }

    chatUpdateTimer->start();
}

void MainWindow::on_logInButton_clicked()
{
    //write username and password to the server
    //QString usernameQStr = ui->usernameInput->text();
    //QString passwordQStr = ui->passwordInput->text();
    QString loginInfoQStr = ui->usernameInput->text() + " " + ui->passwordInput->text();
    /* to be added later
     *if(usernameQStr.count(" ") > 0){
     *  errorfix
     *}
     */
    //const char* username = (usernameQStr+"\n").toUtf8().constData();
    //const char* password = (passwordQStr+"\n").toUtf8().constData();
    const char* loginInfo = (loginInfoQStr+"\n").toUtf8().constData();
    clientSocket->write(loginInfo);
    //clientSocket->write(password);

    ui->stackedWidget->setCurrentWidget(ui->chatPage);
}
