// AIDL kontrak buat UserService Shizuku. Proses ini dijalankan Shizuku
// sebagai UID shell (adb) atau UID root (kalau Shizuku via root), bukan
// UID app biasa — jadi exec() di dalamnya BENERAN punya privilese shell,
// nggak butuh reflection/hidden-API kayak Shizuku.newProcess yang sudah
// deprecated & sering gagal "method not visible" di device modern.
package com.monai.optimizer;

interface IShellUserService {
    // Return format: "<exitCode>\u0001<stdout+stderr>"
    String exec(String cmd);
    void destroy();
}
