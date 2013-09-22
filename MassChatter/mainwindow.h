#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QDateTime>
#include "QDebug"
#include <QTcpSocket>
#include <QTimer>

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

private:
    Ui::MainWindow *ui;

    int PORT;
    QString IP;

    QTcpSocket *clientSocket;
    QTimer *chatUpdateTimer;

};

#endif // MAINWINDOW_H
