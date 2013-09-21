#include "mainwindow.h"
#include "ui_mainwindow.h"
#include <QDateTime>
#include "QDebug"

MainWindow::MainWindow(QWidget *parent) :
    QMainWindow(parent),
    ui(new Ui::MainWindow)
{
    ui->setupUi(this);

    ui->userTextInput->installEventFilter(this);
    ui->chatText->setReadOnly(true);
}

MainWindow::~MainWindow()
{
    delete ui;
}

bool MainWindow::eventFilter(QObject *obj, QEvent *e){
    //this if is used for userTextInput, when the user presses enter (return)
    if(obj==ui->userTextInput && e->type() == QEvent::KeyPress &&
            static_cast<QKeyEvent*>(e)->key() == Qt::Key_Return){
        if(ui->userTextInput->toPlainText() == ""){
            return true;
        }
        ui->chatText->setPlainText(ui->chatText->toPlainText() + "USERNAME[" + QTime::currentTime().toString() + "]: " + ui->userTextInput->toPlainText() + '\n');
        ui->userTextInput->setPlainText("");
        return true;
    }

    return false;
}
