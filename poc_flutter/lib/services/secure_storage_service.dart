import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureStorageService {
  final _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  Future<void> writeData(String key, String value) async {
    await _storage.write(key: key, value: value);
  }

  Future<String?> readData(String key) async {
    return await _storage.read(key: key);
  }

  Future<void> deleteData(String key) async {
    await _storage.delete(key: key);
  }
}
