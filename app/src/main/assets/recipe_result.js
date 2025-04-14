// 頁面加載時獲取食譜內容
document.addEventListener('DOMContentLoaded', function() {
    console.log("Recipe result page loaded");

    // 檢查URL參數
    const urlParams = new URLSearchParams(window.location.search);
    const isSaved = urlParams.get('saved') === 'true';
    const recipeId = urlParams.get('id');

    console.log("URL params check - isSaved:", isSaved, "recipeId:", recipeId);

    if (!isSaved) {
        console.log("This is a new recipe - clearing saved recipe data");

        // 清除之前的狀態
        document.body.removeAttribute('data-saved-recipe-id');

        // 確保保存按鈕可見
        const saveButton = document.querySelector('.save-btn');
        if (saveButton) {
            saveButton.style.display = 'flex';
        }

        // 隱藏刪除按鈕
        const deleteButton = document.getElementById('delete-recipe-btn');
        if (deleteButton) {
            deleteButton.style.display = 'none';
        }

        // 移除已保存標記（如果有）
        const savedBadge = document.querySelector('.saved-badge');
        if (savedBadge) {
            savedBadge.remove();
        }

        console.log("Calling Android.getGeneratedRecipe()");
        Android.getGeneratedRecipe();
    } else {
        console.log("This is a saved recipe with ID:", recipeId);

        // 保存食譜ID到頁面數據中
        if (recipeId) {
            document.body.dataset.savedRecipeId = recipeId;

            // 調用 Android 方法加載特定的保存食譜
            console.log("Calling Android.getSavedRecipe() with ID:", recipeId);
            Android.getSavedRecipe(recipeId);

            // 標記為已保存的食譜
            markAsSavedRecipe(recipeId);
        } else {
            console.error("No recipe ID provided in URL");
            showError("Recipe not found");
        }
    }
});

// 顯示食譜內容
function displayRecipe(recipeContent) {
    document.getElementById('recipe-content').innerHTML = recipeContent;

    // 添加淡入動畫效果
    const elements = document.querySelectorAll('.recipe-content > *');
    elements.forEach((element, index) => {
        element.style.opacity = '0';
        element.style.transform = 'translateY(20px)';
        element.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        element.style.transitionDelay = `${index * 0.1}s`;

        setTimeout(() => {
            element.style.opacity = '1';
            element.style.transform = 'translateY(0)';
        }, 100);
    });
}

// 顯示食譜圖片
function displayRecipeImage(imageUrl) {
    const imageContainer = document.getElementById('recipe-image-container');
    const recipeImage = document.getElementById('recipe-image');

    recipeImage.src = imageUrl;
    imageContainer.style.display = 'block';
}

// 保存食譜
function saveRecipe() {
    // 獲取食譜內容和標題
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    const recipeTitle = document.querySelector('#recipe-content h1')?.textContent || 'Untitled Recipe';

    // 獲取圖片 URL（如果有）
    let imageUrl = '';
    const recipeImage = document.getElementById('recipe-image');
    if (recipeImage && recipeImage.src) {
        imageUrl = recipeImage.src;
    }

    // 添加保存中的視覺反饋
    const saveButton = document.querySelector('.save-btn');
    const originalText = saveButton.innerHTML;
    saveButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
    saveButton.disabled = true;

    // 調用 Android 方法保存食譜
    setTimeout(() => {
        Android.saveRecipe(recipeTitle, recipeContent, imageUrl);
        // 注意：實際保存後的UI更新會在 markAsSavedRecipe 函數中處理
    }, 500); // 添加短暫延遲以顯示保存動畫
}

// 分享食譜
function shareRecipe() {
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    const recipeTitle = document.querySelector('#recipe-content h1')?.textContent || 'My Recipe';

    // 添加分享中的視覺反饋
    const shareButton = document.querySelector('.share-btn');
    const originalText = shareButton.innerHTML;
    shareButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sharing...';

    setTimeout(() => {
        Android.shareRecipe(recipeTitle, recipeContent);
        shareButton.innerHTML = originalText;
    }, 500); // 添加短暫延遲以顯示分享動畫
}

// 返回主頁
function goToHome() {
    window.location.href = "home.html";
}

// 標記為已保存的食譜
function markAsSavedRecipe(recipeId) {
    // 保存食譜ID到頁面數據中
    document.body.dataset.savedRecipeId = recipeId;

    // 隱藏保存按鈕
    const saveButton = document.querySelector('.save-btn');
    if (saveButton) {
        saveButton.style.display = 'none';
    }

    // 顯示刪除按鈕
    const deleteButton = document.getElementById('delete-recipe-btn');
    if (deleteButton) {
        deleteButton.style.display = 'flex';
    }

    // 添加一個標記，表明這是已保存的食譜
    const recipeContainer = document.querySelector('.recipe-result-container');

    // 檢查是否已經有標記，避免重複添加
    if (!document.querySelector('.saved-badge')) {
        const savedBadge = document.createElement('div');
        savedBadge.className = 'saved-badge';
        savedBadge.textContent = 'Saved Recipe';
        recipeContainer.appendChild(savedBadge);
    }
}

// 顯示刪除確認對話框
function confirmDeleteRecipe() {
    document.getElementById('delete-confirm-modal').style.display = 'flex';
}

// 取消刪除
function cancelDelete() {
    document.getElementById('delete-confirm-modal').style.display = 'none';
}

// 刪除食譜
function deleteRecipe() {
    const recipeId = document.body.dataset.savedRecipeId;
    if (recipeId) {
        // 添加刪除中的視覺反饋
        const deleteButton = document.querySelector('.modal-confirm');
        const originalText = deleteButton.innerHTML;
        deleteButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Deleting...';
        deleteButton.disabled = true;

        setTimeout(() => {
            Android.deleteRecipe(parseInt(recipeId));
            document.getElementById('delete-confirm-modal').style.display = 'none';
        }, 500); // 添加短暫延遲以顯示刪除動畫
    } else {
        console.error("No recipe ID found for deletion");
        document.getElementById('delete-confirm-modal').style.display = 'none';
    }
}

// 顯示錯誤信息
function showError(message) {
    document.getElementById('recipe-content').innerHTML = `
        <div class="error-message">
            <i class="fas fa-exclamation-circle"></i>
            <p>${message}</p>
        </div>
    `;
}