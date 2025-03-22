// 頁面加載時獲取食譜內容
document.addEventListener('DOMContentLoaded', function() {
    console.log("Recipe result page loaded");
    // 從 Android 獲取生成的食譜
    Android.getGeneratedRecipe();
});

// 顯示生成的食譜
function displayRecipe(recipeHtml) {
    console.log("Displaying recipe content");
    const recipeContent = document.getElementById('recipe-content');
    recipeContent.innerHTML = recipeHtml;
    
    // 注意：不需要在這裡調用 generateRecipeImage，
    // 因為我們已經在 getGeneratedRecipe 方法中調用了
}

// 顯示生成的食譜圖片
function displayRecipeImage(imageUrl) {
    console.log("Displaying recipe image (starts with): " + imageUrl.substring(0, 30) + "...");
    const recipeImage = document.getElementById('recipe-image');
    const recipeImageContainer = document.getElementById('recipe-image-container');
    
    // 設置圖片 URL (可以是 data URL 或普通 URL)
    recipeImage.src = imageUrl;
    
    // 圖片加載完成後顯示
    recipeImage.onload = function() {
        console.log("Image loaded successfully");
        recipeImageContainer.style.display = 'block';
    };
    
    // 圖片加載失敗處理
    recipeImage.onerror = function(e) {
        console.error('Failed to load recipe image', e);
        alert('無法載入圖片，請重試');
    };
}

// 保存食譜
function saveRecipe() {
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    const recipeImage = document.getElementById('recipe-image').src || '';
    
    // 從食譜內容中提取標題
    let recipeTitle = "";
    const titleMatch = recipeContent.match(/<h1>(.*?)<\/h1>/);
    if (titleMatch && titleMatch[1]) {
        recipeTitle = titleMatch[1];
    } else {
        // 如果沒有找到 h1 標籤，使用默認標題
        recipeTitle = "Delicious Recipe";
    }
    
    // 調用 Android 方法保存食譜
    Android.saveRecipe(recipeTitle, recipeContent, recipeImage);
}

// 分享食譜
function shareRecipe() {
    const recipeContent = document.getElementById('recipe-content').innerHTML;
    Android.shareRecipe(recipeContent);
}

// 返回主頁
function goToHome() {
    window.location.href = "home.html";
} 