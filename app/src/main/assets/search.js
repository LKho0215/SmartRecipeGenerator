// 全局變量
let allRecipes = [];
let filteredRecipes = [];

// 頁面加載時初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log("Search page loaded");

    // 從 Android 獲取所有保存的食譜，使用與 home 頁面相同的方法
    Android.loadSavedRecipes();

    // 監聽搜索框的清除按鈕
    document.getElementById('clear-search').addEventListener('click', function() {
        document.getElementById('search-input').value = '';
        searchRecipes();
    });
});

// 顯示所有保存的食譜（由 Android 調用）
function displaySavedRecipes(recipesJson) {
    console.log("Displaying saved recipes");

    // 隱藏加載動畫
    document.getElementById('loading-container').style.display = 'none';

    try {
        // 解析食譜數據
        allRecipes = JSON.parse(recipesJson);
        filteredRecipes = [...allRecipes];

        // 更新結果計數
        updateResultsCount(allRecipes.length);

        // 顯示食譜
        renderRecipes(allRecipes);
    } catch (error) {
        console.error("Error parsing recipes JSON:", error);
        showNoResults("Error loading recipes");
    }
}

// 搜索食譜
function searchRecipes() {
    const searchTerm = document.getElementById('search-input').value.trim().toLowerCase();

    if (searchTerm === '') {
        // 如果搜索詞為空，顯示所有食譜
        filteredRecipes = [...allRecipes];
        updateResultsCount(filteredRecipes.length);
    } else {
        // 過濾食譜
        filteredRecipes = allRecipes.filter(recipe => {
            return recipe.title.toLowerCase().includes(searchTerm) ||
                   (recipe.content && recipe.content.toLowerCase().includes(searchTerm));
        });

        // 更新結果計數
        updateResultsCount(filteredRecipes.length, searchTerm);
    }

    // 顯示過濾後的食譜
    renderRecipes(filteredRecipes);
}

// 清除搜索
function clearSearch() {
    document.getElementById('search-input').value = '';
    searchRecipes();
}

// 更新結果計數
function updateResultsCount(count, searchTerm = '') {
    const resultsCountElement = document.getElementById('results-count');

    if (searchTerm === '') {
        if (count === 0) {
            resultsCountElement.textContent = 'No saved recipes';
        } else {
            resultsCountElement.textContent = `All saved recipes (${count})`;
        }
    } else {
        if (count === 0) {
            resultsCountElement.textContent = `No results for "${searchTerm}"`;
        } else {
            resultsCountElement.textContent = `${count} result${count > 1 ? 's' : ''} for "${searchTerm}"`;
        }
    }
}

// 顯示食譜
function renderRecipes(recipes) {
    const recipesContainer = document.getElementById('recipes-container');

    // 清除之前的內容（保留加載動畫）
    const loadingContainer = document.getElementById('loading-container');
    recipesContainer.innerHTML = '';
    if (loadingContainer) {
        recipesContainer.appendChild(loadingContainer);
    }

    // 顯示或隱藏無結果提示
    if (recipes.length === 0) {
        document.getElementById('no-results').style.display = 'flex';
    } else {
        document.getElementById('no-results').style.display = 'none';

        // 創建食譜卡片
        recipes.forEach((recipe, index) => {
            const recipeCard = createRecipeCard(recipe, index);
            recipesContainer.appendChild(recipeCard);
        });
    }
}

// 創建食譜卡片
function createRecipeCard(recipe, index) {
    const card = document.createElement('div');
    card.className = 'recipe-card';
    card.style.animationDelay = `${index * 0.05}s`;
    card.setAttribute('data-id', recipe.id);
    card.onclick = function() {
        // 使用與 home 頁面相同的方法打開食譜
        const recipeId = this.getAttribute('data-id');
        console.log("Recipe clicked:", recipeId);
        Android.loadRecipeDetails(parseInt(recipeId));
    };

    // 提取食譜描述（如果有）
    let description = '';
    if (recipe.content) {
        // 嘗試從內容中提取第一段非標題文本作為描述
        const contentElement = document.createElement('div');
        contentElement.innerHTML = recipe.content;
        const paragraphs = contentElement.querySelectorAll('p');
        if (paragraphs.length > 0) {
            description = paragraphs[0].textContent;
        }
    }

    // 提取食譜類型（如果有）
    let tags = [];
    if (recipe.content) {
        // 嘗試從內容中查找可能的食譜類型標籤
        const lowerContent = recipe.content.toLowerCase();
        const possibleTags = ['vegan', 'vegetarian', 'gluten-free', 'dairy-free', 'low-carb', 'keto', 'paleo'];
        tags = possibleTags.filter(tag => lowerContent.includes(tag));
    }

    // 構建卡片 HTML
    card.innerHTML = `
        <img src="${recipe.imageUrl || 'default_recipe.jpg'}" alt="${recipe.title}" class="recipe-image" onerror="this.src='default_recipe.jpg'">
        <div class="recipe-content">
            <h3 class="recipe-title">${recipe.title}</h3>
            ${description ? `<p class="recipe-description">${description}</p>` : ''}
            ${tags.length > 0 ? `
                <div class="recipe-tags">
                    ${tags.map(tag => `<span class="recipe-tag">${tag}</span>`).join('')}
                </div>
            ` : ''}
        </div>
    `;

    return card;
}

// 顯示無結果提示
function showNoResults(message = "No recipes found") {
    document.getElementById('loading-container').style.display = 'none';
    document.getElementById('no-results').style.display = 'flex';
    document.getElementById('no-results').querySelector('p').textContent = message;
}

// 返回主頁
function goToHome() {
    window.location.href = "home.html";
}
