function signIn() {

    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;
    var message = document.getElementById('message');

    if (email && password) {
        Android.signIn(email, password);
    } else {
        message.textContent = "Please fill in all fields";
        message.style.color = "red";
    }
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
    console.log("Page loaded");

    // 檢查當前頁面
    const currentPage = window.location.pathname.split('/').pop();
    console.log("Current page:", currentPage);

    // 只在主頁(home.html)調用這些方法
    if (currentPage === 'home.html') {
        console.log("Home page loaded");

        // 檢查用戶是否已登錄
        if (typeof Android !== 'undefined') {
            console.log("Android object is available");

            // 加載已保存的食譜
            console.log("Calling Android.loadSavedRecipes()");
            Android.loadSavedRecipes();
        } else {
            console.log("Android object is NOT available");
        }
    }

    // 只在個人資料頁面(profile.html)調用這些方法
    if (currentPage === 'profile.html') {
        console.log("Profile page loaded");

        // 檢查用戶是否已登錄
        if (typeof Android !== 'undefined') {
            // 調用 Android 方法獲取用戶信息
            Android.getUserInfo();
        }
    }
});

// 更新用戶信息的函數（由 Android 調用）
function updateUserInfo(name, email) {
    document.getElementById('username').textContent = name;
    document.getElementById('useremail').textContent = email;
}

function editProfile() {
    window.location.href = "edit_profile.html";
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

// 顯示已保存的食譜
function displaySavedRecipes(recipesJson) {
    console.log("displaySavedRecipes called with data:", recipesJson);

    const recipesSection = document.querySelector('.recipes');
    if (!recipesSection) {
        console.error("Could not find .recipes element");
        return;
    }

    let recipes;
    try {
        recipes = JSON.parse(recipesJson);
        console.log("Parsed recipes:", recipes);
    } catch (e) {
        console.error("Error parsing recipes JSON:", e);
        return;
    }

    // 保留標題和視圖全部按鈕
    const sectionHeader = recipesSection.querySelector('.section-header');

    // 清空現有內容，但保留標題
    recipesSection.innerHTML = '';
    if (sectionHeader) {
        recipesSection.appendChild(sectionHeader);
    } else {
        // 如果沒有找到標題，創建一個新的
        const header = document.createElement('div');
        header.className = 'section-header';
        header.innerHTML = '<h2>Saved Recipes</h2><span class="view-all" onclick="location.href=\'saved_recipes.html\'">View All</span>';
        recipesSection.appendChild(header);
    }

    if (recipes.length === 0) {
        console.log("No recipes found");
        const noRecipes = document.createElement('p');
        noRecipes.className = 'no-recipes';
        noRecipes.textContent = 'No saved recipes yet. Create your first recipe!';
        recipesSection.appendChild(noRecipes);
        return;
    }

    // 創建水平滾動容器
    console.log("Creating horizontal recipe container for", recipes.length, "recipes");
    const recipeContainer = document.createElement('div');
    recipeContainer.className = 'recipes-scroll-container';

    recipes.forEach(recipe => {
        console.log("Adding recipe:", recipe.title);
        const recipeCard = document.createElement('div');
        recipeCard.className = 'recipe-scroll-card';
        recipeCard.setAttribute('data-id', recipe.id);

        // 添加點擊事件
        recipeCard.addEventListener('click', function() {
            const recipeId = this.getAttribute('data-id');
            console.log("Recipe clicked:", recipeId);
            Android.loadRecipeDetails(parseInt(recipeId));
        });

        // 添加圖片
        let imageHtml = '';
        if (recipe.imageUrl && recipe.imageUrl.trim() !== '') {
            imageHtml = `<div class="recipe-image"><img src="${recipe.imageUrl}" alt="${recipe.title}"></div>`;
        } else {
            imageHtml = `<div class="recipe-image no-image"><span>No Image</span></div>`;
        }

        // 添加標題
        const titleHtml = `<div class="recipe-title">${recipe.title}</div>`;

        recipeCard.innerHTML = imageHtml + titleHtml;
        recipeContainer.appendChild(recipeCard);
    });

    recipesSection.appendChild(recipeContainer);
    console.log("Recipe container added to DOM");
}

// 編輯個人資料相關函數

// 顯示編輯表單
function showEditForm(formType) {
    // 隱藏所有表單
    document.querySelectorAll('.edit-form').forEach(form => {
        form.classList.remove('active');
    });

    // 顯示選定的表單
    document.getElementById(formType + '-form').classList.add('active');

    // 清除錯誤消息
    document.querySelectorAll('.error-message').forEach(error => {
        error.textContent = '';
    });

    // 清除輸入框
    document.querySelectorAll('input').forEach(input => {
        input.value = '';
    });
}

// 取消編輯
function cancelEdit() {
    document.querySelectorAll('.edit-form').forEach(form => {
        form.classList.remove('active');
    });
}

// 更新用戶名
function updateName() {
    const newName = document.getElementById('new-name').value.trim();
    const errorElement = document.getElementById('name-error');

    // 驗證輸入
    if (!newName) {
        errorElement.textContent = 'Name cannot be empty';
        return;
    }

    // 調用 Android 方法更新用戶名
    Android.updateUserName(newName);
}

// 更新郵箱
function updateEmail() {
    const newEmail = document.getElementById('new-email').value.trim();
    const errorElement = document.getElementById('email-error');

    // 驗證輸入
    if (!newEmail) {
        errorElement.textContent = 'Email cannot be empty';
        return;
    }

    if (!isValidEmail(newEmail)) {
        errorElement.textContent = 'Please enter a valid email address';
        return;
    }

    // 調用 Android 方法更新郵箱
    Android.updateUserEmail(newEmail);
}

// 更新密碼
function updatePassword() {
    const currentPassword = document.getElementById('current-password').value;
    const newPassword = document.getElementById('new-password').value;
    const confirmPassword = document.getElementById('confirm-password').value;
    const errorElement = document.getElementById('password-error');

    // 驗證輸入
    if (!currentPassword) {
        errorElement.textContent = 'Current password is required';
        return;
    }

    if (!newPassword) {
        errorElement.textContent = 'New password is required';
        return;
    }

    if (newPassword.length < 6) {
        errorElement.textContent = 'Password must be at least 6 characters';
        return;
    }

    if (newPassword !== confirmPassword) {
        errorElement.textContent = 'New passwords do not match';
        return;
    }

    // 調用 Android 方法更新密碼
    Android.updateUserPassword(currentPassword, newPassword);
}

// 驗證郵箱格式
function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

// 處理更新成功
function handleUpdateSuccess(message) {
    alert(message);
    window.location.href = "profile.html";
}

// 處理更新失敗
function handleUpdateError(errorMessage) {
    const activeForm = document.querySelector('.edit-form.active');
    if (activeForm) {
        const errorElement = activeForm.querySelector('.error-message');
        if (errorElement) {
            errorElement.textContent = errorMessage;
        }
    }
}

// 顯示過渡動畫
function showTransition() {
    // 創建過渡元素
    const transition = document.createElement('div');
    transition.className = 'page-transition';

    // 創建加載指示器
    const loader = document.createElement('div');
    loader.className = 'loader';
    transition.appendChild(loader);

    // 添加歡迎文字
    const welcomeText = document.createElement('div');
    welcomeText.className = 'welcome-text';
    welcomeText.innerHTML = '<h2>Welcome</h2><p>Preparing your recipes...</p>';
    transition.appendChild(welcomeText);

    // 添加到頁面
    document.body.appendChild(transition);
}

// 處理登錄成功
function handleLoginSuccess() {
    // 這個函數將由 Android 在登錄成功時調用
    // 過渡動畫已經顯示，Android 將在適當的時候加載 home.html
    showTransition();
}

// 處理登錄錯誤
function handleLoginError(errorMessage) {
    // 移除過渡動畫
    const transition = document.querySelector('.page-transition');
    if (transition) {
        transition.classList.add('fade-out');
        setTimeout(() => {
            transition.remove();
        }, 300);
    }

    // 顯示錯誤信息
    alert(errorMessage);
}