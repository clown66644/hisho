import 'package:flutter/material.dart';
import 'services/alarm_service.dart';
import 'services/secure_storage_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final alarmService = AlarmService();
  await alarmService.init();

  runApp(MyApp(alarmService: alarmService));
}

class MyApp extends StatelessWidget {
  final AlarmService alarmService;

  const MyApp({super.key, required this.alarmService});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'POC Flutter 検証',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: HomeScreen(alarmService: alarmService),
    );
  }
}

class HomeScreen extends StatefulWidget {
  final AlarmService alarmService;

  const HomeScreen({super.key, required this.alarmService});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _secureStorage = SecureStorageService();
  String _status = 'ステータス: 待機中';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('POC Flutter 検証モジュール'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ElevatedButton(
              onPressed: () async {
                setState(() {
                  _status = 'ステータス: フォアグラウンド通知を待機中...';
                });
                await widget.alarmService.showForegroundDelayDemo(
                  1,
                  '執事 (Flutter) からの通知',
                  'フォアグラウンド遅延デモが完了しました',
                  const Duration(seconds: 10),
                );
                setState(() {
                  _status = 'ステータス: フォアグラウンド通知表示完了';
                });
              },
              child: const Text('フォアグラウンド通知デモ (10秒後)'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                const testValue = 'non-secret-test-value';
                await _secureStorage.writeData('storage_probe', testValue);
                final value = await _secureStorage.readData('storage_probe');
                setState(() {
                  _status = value == testValue
                      ? '暗号化ストレージの往復を確認しました（値は非表示）'
                      : '暗号化ストレージの確認に失敗しました';
                });
              },
              child: const Text('flutter_secure_storage テスト'),
            ),
            const SizedBox(height: 32),
            Text(
              _status,
              style: const TextStyle(fontSize: 16),
            ),
          ],
        ),
      ),
    );
  }
}
