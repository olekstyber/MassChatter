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

private:

    int UPDATE_CHAT_TIME;

    Ui::MainWindow *ui;

    int PORT;
    QString IP;

    QTcpSocket *clientSocket;
    QTimer *chatUpdateTimer;

    QStackedWidget *mainWidgetStack;

};

#endif // MAINWINDOW_H
