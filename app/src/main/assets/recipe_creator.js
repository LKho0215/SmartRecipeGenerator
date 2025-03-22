// 全局變量
let selectedTypes = [];
let selectedIngredients = [];

// 頁面加載時初始化
document.addEventListener('DOMContentLoaded', function() {
    // 加載用戶的 Pantry 食材
    Android.getPantryItemsForRecipe();
});

// 選擇食譜類型
function selectType(type) {
    const typeElement = event.currentTarget;
    
    if (typeElement.classList.contains('selected')) {
        // 如果已選中，則取消選中
        typeElement.classList.remove('selected');
        selectedTypes = selectedTypes.filter(t => t !== type);
    } else {
        // 如果未選中，則選中
        typeElement.classList.add('selected');
        if (!selectedTypes.includes(type)) {
            selectedTypes.push(type);
        }
    }
    
    updateSelectedTypes();
}

// 添加自定義類型
function addCustomType() {
    const customTypeInput = document.getElementById('custom-type');
    const customType = customTypeInput.value.trim();
    
    if (customType && !selectedTypes.includes(customType)) {
        selectedTypes.push(customType);
        customTypeInput.value = '';
        updateSelectedTypes();
    }
}

// 更新已選擇的類型顯示
function updateSelectedTypes() {
    const selectedTypesContainer = document.getElementById('selected-types');
    selectedTypesContainer.innerHTML = '';
    
    if (selectedTypes.length === 0) {
        return;
    }
    
    selectedTypes.forEach(type => {
        const typeElement = document.createElement('div');
        typeElement.className = 'selected-type';
        typeElement.innerHTML = `
            ${type}
            <span class="remove" onclick="removeType('${type}')">&times;</span>
        `;
        selectedTypesContainer.appendChild(typeElement);
    });
}

// 移除已選擇的類型
function removeType(type) {
    selectedTypes = selectedTypes.filter(t => t !== type);
    
    // 更新類型選項的選中狀態
    document.querySelectorAll('.type-option').forEach(option => {
        if (option.textContent.trim() === type) {
            option.classList.remove('selected');
        }
    });
    
    updateSelectedTypes();
}

// 選擇食材
function selectIngredient(id, name) {
    const ingredientElement = event.currentTarget;
    
    if (ingredientElement.classList.contains('selected')) {
        // 如果已選中，則取消選中
        ingredientElement.classList.remove('selected');
        selectedIngredients = selectedIngredients.filter(i => i.id !== id);
    } else {
        // 如果未選中，則選中
        ingredientElement.classList.add('selected');
        if (!selectedIngredients.some(i => i.id === id)) {
            selectedIngredients.push({ id, name });
        }
    }
    
    updateSelectedIngredients();
}

// 更新已選擇的食材顯示
function updateSelectedIngredients() {
    const ingredientsList = document.getElementById('ingredients-list');
    const emptySelection = document.querySelector('.empty-selection');
    
    ingredientsList.innerHTML = '';
    
    if (selectedIngredients.length === 0) {
        ingredientsList.appendChild(emptySelection);
        return;
    }
    
    selectedIngredients.forEach(ingredient => {
        const ingredientElement = document.createElement('div');
        ingredientElement.className = 'selected-ingredient';
        ingredientElement.innerHTML = `
            ${ingredient.name}
            <span class="remove" onclick="removeIngredient(${ingredient.id})">&times;</span>
        `;
        ingredientsList.appendChild(ingredientElement);
    });
}

// 移除已選擇的食材
function removeIngredient(id) {
    selectedIngredients = selectedIngredients.filter(i => i.id !== id);
    
    // 更新食材選項的選中狀態
    document.querySelectorAll('.pantry-item').forEach(item => {
        if (parseInt(item.dataset.id) === id) {
            item.classList.remove('selected');
        }
    });
    
    updateSelectedIngredients();
}

// 更新 Pantry 食材列表（由 Android 調用）
function updatePantryItemsForRecipe(itemsJson) {
    const pantryItemsContainer = document.getElementById('pantry-items');
    pantryItemsContainer.innerHTML = '';
    
    const items = JSON.parse(itemsJson);
    
    if (items.length === 0) {
        pantryItemsContainer.innerHTML = '<div class="empty-pantry">Your pantry is empty. Add ingredients in the Pantry page.</div>';
        return;
    }
    
    items.forEach(item => {
        const itemElement = document.createElement('div');
        itemElement.className = 'pantry-item';
        itemElement.dataset.id = item.id;
        itemElement.textContent = item.name;
        itemElement.onclick = function() { selectIngredient(item.id, item.name); };
        pantryItemsContainer.appendChild(itemElement);
    });
}

// 下一步
function nextStep(currentStep) {
    // 驗證當前步驟
    if (currentStep === 1 && selectedTypes.length === 0) {
        alert('Please select at least one recipe type');
        return;
    }
    
    if (currentStep === 2 && selectedIngredients.length === 0) {
        alert('Please select at least one ingredient');
        return;
    }
    
    // 隱藏當前步驟，顯示下一步
    document.getElementById(`step-${currentStep}-content`).classList.remove('active');
    document.getElementById(`step-${currentStep+1}-content`).classList.add('active');
    
    // 更新步驟指示器
    document.getElementById(`step-${currentStep}`).classList.remove('active');
    document.getElementById(`step-${currentStep+1}`).classList.add('active');
    
    // 如果進入第三步，更新摘要
    if (currentStep === 2) {
        updateRecipeSummary();
    }
}

// 上一步
function prevStep(currentStep) {
    // 隱藏當前步驟，顯示上一步
    document.getElementById(`step-${currentStep}-content`).classList.remove('active');
    document.getElementById(`step-${currentStep-1}-content`).classList.add('active');
    
    // 更新步驟指示器
    document.getElementById(`step-${currentStep}`).classList.remove('active');
    document.getElementById(`step-${currentStep-1}`).classList.add('active');
}

// 更新食譜摘要
function updateRecipeSummary() {
    const summaryTypes = document.getElementById('summary-types');
    const summaryIngredients = document.getElementById('summary-ingredients');
    
    // 更新類型摘要
    summaryTypes.innerHTML = '';
    if (selectedTypes.length > 0) {
        selectedTypes.forEach(type => {
            const typeElement = document.createElement('div');
            typeElement.className = 'summary-item';
            typeElement.textContent = type;
            summaryTypes.appendChild(typeElement);
        });
    } else {
        summaryTypes.innerHTML = '<div class="empty-summary">No recipe types selected</div>';
    }
    
    // 更新食材摘要
    summaryIngredients.innerHTML = '';
    if (selectedIngredients.length > 0) {
        selectedIngredients.forEach(ingredient => {
            const ingredientElement = document.createElement('div');
            ingredientElement.className = 'summary-item';
            ingredientElement.textContent = ingredient.name;
            summaryIngredients.appendChild(ingredientElement);
        });
    } else {
        summaryIngredients.innerHTML = '<div class="empty-summary">No ingredients selected</div>';
    }
}

// 生成食譜
function generateRecipe() {
    // 顯示加載動畫
    document.getElementById('loading-recipe').style.display = 'block';
    document.getElementById('generate-btn').disabled = true;
    
    // 準備數據
    const recipeData = {
        types: selectedTypes,
        ingredients: selectedIngredients.map(i => i.name)
    };
    
    // 調用 Android 方法生成食譜
    Android.generateRecipe(JSON.stringify(recipeData));
}

// 顯示生成的食譜（由 Android 調用）
function displayGeneratedRecipe(recipeContent) {
    console.log("Displaying generated recipe");
    document.getElementById('loading-recipe').style.display = 'none';
    document.getElementById('recipe-result').innerHTML = recipeContent;
    document.getElementById('recipe-result-container').style.display = 'block';
    document.getElementById('generate-btn').disabled = false;
}

// 保存食譜
function saveRecipe() {
    const recipeContent = document.getElementById('recipe-result').innerHTML;
    const recipeTypes = selectedTypes.join(', ');
    
    // 從食譜內容中提取標題（假設第一行是標題）
    let recipeTitle = recipeContent.split('\n')[0];
    // 如果標題太長，截斷它
    if (recipeTitle.length > 50) {
        recipeTitle = recipeTitle.substring(0, 47) + '...';
    }
    
    // 調用 Android 方法保存食譜
    Android.saveRecipe(recipeTitle, recipeContent);
}

// 返回主頁
function goBack() {
    window.location.href = "home.html";
}

// 監聽自定義類型輸入框的回車事件
document.getElementById('custom-type').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        addCustomType();
    }
}); 