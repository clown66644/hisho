import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class AlarmService {
  final FlutterLocalNotificationsPlugin _notificationsPlugin =
      FlutterLocalNotificationsPlugin();

  Future<void> init() async {
    const androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidSettings);

    await _notificationsPlugin.initialize(initSettings);
  }

  /// フォアグラウンド中だけ成立する比較用デモ。
  /// Exact Alarm、Doze、強制終了、再起動復旧の検証には使用できない。
  Future<void> showForegroundDelayDemo(
      int id, String title, String body, Duration delay) async {
    const androidDetails = AndroidNotificationDetails(
      'butler_flutter_channel',
      '執事・秘書 Flutter アラーム通知',
      channelDescription: 'Flutter POC 用 Exact Alarm 通知チャンネル',
      importance: Importance.max,
      priority: Priority.high,
    );

    const notificationDetails = NotificationDetails(android: androidDetails);

    await Future.delayed(delay);
    await _notificationsPlugin.show(id, title, body, notificationDetails);
  }
}
