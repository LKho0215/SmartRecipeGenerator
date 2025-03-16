function signIn() {
    var email = document.getElementById('email').value;
    var password = document.getElementById('password').value;
    var message = document.getElementById('message');

    Android.signIn(email, password);
}

function signUp() {
    var email = document.getElementById('email').value;
    var password = document.getElementById('password').value;
    var message = document.getElementById('message');

    if (email && password) {
        Android.signUp(email, password);
    } else {
        message.textContent = "Please fill in all fields";
        message.style.color = "red";
    }
}

function snapPhoto() {
    Android.snapPhoto();
}

function viewProfile(){
    window.location.href = "profile.html";
}

// 頁面加載時獲取用戶信息
document.addEventListener('DOMContentLoaded', function() {
    // 調用 Android 方法獲取用戶信息
    Android.getUserInfo();
});

// 更新用戶信息的函數（由 Android 調用）
function updateUserInfo(name, email) {
    document.getElementById('username').textContent = name;
    document.getElementById('useremail').textContent = email;
}

function editProfile() {
    Android.editProfile();
}

function logout() {
    Android.logout();
}

// 顯示刪除確認彈窗
function showDeleteConfirm() {
    document.getElementById('delete-modal').style.display = 'flex';
}

// 取消刪除
function cancelDelete() {
    document.getElementById('delete-modal').style.display = 'none';
}

// 確認刪除
function confirmDelete() {
    document.getElementById('delete-modal').style.display = 'none';
    console.log("User confirmed deletion");
    Android.deleteAccount();
}