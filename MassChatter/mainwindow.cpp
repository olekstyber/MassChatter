#include "mainwindow.h"
#include "ui_mainwindow.h"

MainWindow::MainWindow(QWidget *parent) :
    QMainWindow(parent),
    ui(new Ui::MainWindow)
{
    ui->setupUi(this);

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
    chatUpdateTimer->start(200);

}

MainWindow::~MainWindow()
{
    delete ui;
    delete clientSocket;
    delete chatUpdateTimer;
}

bool MainWindow::eventFilter(QObject *obj, QEvent *e){
    //this if is used for userTextInput, when the user presses enter (return)
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

void MainWindow::updateChat(){

    ui->chatText->setPlainText(ui->chatText->toPlainText() + QString(clientSocket->readAll()));

    chatUpdateTimer->start();
}
