#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QDateTime>
#include "QDebug"
#include <QTcpSocket>
#include <QTimer>
#include <QScrollBar>
#include <QStackedWidget>
#include "ui_mainwindow.h"

namespace Ui {
class MainWindow;
}

class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = 0);
    ~MainWindow();

public slots:
    void updateChat();

protected:
     bool eventFilter(QObject *obj, QEvent *e);

private slots:

     void on_logInButton_clicked();

     void on_registerButton_clicked();

     void on_refreshRoomsButton_clicked();

     void on_createNewRoomButton_clicked();

     void on_createRoomButton_clicked();

     void on_joinRoomButton_clicked();

private:

    int UPDATE_CHAT_TIME;

    enum LOGOUT_TYPE{
        CLOSE_LOGOUT = 0,
        BUTTON_LOGOUT,
        MESSAGE_LOGOUT
    };

    Ui::MainWindow *ui;

    int PORT;
    QString IP;

    QTcpSocket *clientSocket;
    QTimer *chatUpdateTimer;

    QStackedWidget *mainWidgetStack;

    void closeEvent(QCloseEvent *event);
    void logout(LOGOUT_TYPE t);
    void updateRoomSelectContents();
    QString recieveDataFromServer(bool ignoreReadyRead);
    void writeDataToServer(QString data);

};

#endif // MAINWINDOW_H
