// 頁面加載時獲取 pantry 物品
document.addEventListener('DOMContentLoaded', function() {
    console.log("Pantry page loaded, requesting items...");
    // 調用 Android 方法獲取 pantry 物品
    Android.getPantryItems();
});

// 顯示 pantry 物品（由 Android 調用）
function displayPantryItems(itemsJson) {
    console.log("Displaying pantry items:", itemsJson);
    const pantryList = document.getElementById('pantry-list');
    const emptyMessage = document.getElementById('empty-message');
    
    // 清空現有列表（除了空消息）
    const children = Array.from(pantryList.children);
    for (const child of children) {
        if (child !== emptyMessage) {
            pantryList.removeChild(child);
        }
    }
    
    try {
        // 解析 JSON 數據
        const items = typeof itemsJson === 'string' ? JSON.parse(itemsJson) : itemsJson;
        
        if (items.length === 0) {
            // 顯示空消息
            emptyMessage.style.display = 'block';
        } else {
            // 隱藏空消息
            emptyMessage.style.display = 'none';
            
            // 添加每個物品到列表
            items.forEach(item => {
                const itemElement = document.createElement('div');
                itemElement.className = 'pantry-item';
                itemElement.innerHTML = `
                    <span class="item-name">${item.name}</span>
                    <button class="delete-btn" onclick="removeFromPantry(${item.id})">Delete</button>
                `;
                pantryList.appendChild(itemElement);
            });
        }
    } catch (e) {
        console.error("Error parsing pantry items:", e);
        emptyMessage.style.display = 'block';
        emptyMessage.textContent = "Error loading pantry items";
    }
}

// 添加新物品
function addItem() {
    const newItemInput = document.getElementById('new-item');
    const itemName = newItemInput.value.trim();
    
    if (itemName) {
        console.log("Adding item:", itemName);
        Android.addPantryItem(itemName);
        newItemInput.value = ''; // 清空輸入框
    }
}

// 刪除物品
function removeFromPantry(itemId) {
    console.log("Removing item:", itemId);
    Android.removeFromPantry(itemId);
}

// 返回主頁
function goBack() {
    window.location.href = "home.html";
}

// 加載 Pantry 物品（可以從其他地方調用）
function loadPantryItems() {
    console.log("Reloading pantry items...");
    Android.getPantryItems();
} 