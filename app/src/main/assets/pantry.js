// 頁面加載時獲取 pantry 物品
document.addEventListener('DOMContentLoaded', function() {
    console.log("Pantry page loaded, requesting items...");
    // 調用 Android 方法獲取 pantry 物品
    if (typeof Android !== 'undefined') {
        Android.getPantryItems();
    } else {
        console.log("Android interface not available");
        // 測試數據（僅用於開發）
        const testItems = [
            { id: 1, name: "Tomatoes" },
            { id: 2, name: "Chicken" },
            { id: 3, name: "Rice" }
        ];
        displayPantryItems(JSON.stringify(testItems));
    }

    // 添加回車鍵監聽器
    const newItemInput = document.getElementById('new-item');
    newItemInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            addItem();
        }
    });
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

            // 添加每個物品到列表，使用延遲添加動畫效果
            items.forEach((item, index) => {
                setTimeout(() => {
                    const itemElement = document.createElement('div');
                    itemElement.className = 'pantry-item';
                    itemElement.innerHTML = `
                        <span class="item-name">${item.name}</span>
                        <button class="delete-btn" onclick="removeFromPantry(${item.id})">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    `;
                    pantryList.appendChild(itemElement);
                }, index * 50); // 每個項目延遲 50ms 添加
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

        // 添加加載動畫
        const addBtn = document.getElementById('add-btn');
        const originalText = addBtn.innerHTML;
        addBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        addBtn.disabled = true;

        // 調用 Android 方法
        if (typeof Android !== 'undefined') {
            Android.addPantryItem(itemName);
        } else {
            // 測試模式
            setTimeout(() => {
                const testItems = [
                    { id: 1, name: "Tomatoes" },
                    { id: 2, name: "Chicken" },
                    { id: 3, name: "Rice" },
                    { id: Math.floor(Math.random() * 1000), name: itemName }
                ];
                displayPantryItems(JSON.stringify(testItems));

                // 恢復按鈕
                addBtn.innerHTML = originalText;
                addBtn.disabled = false;
            }, 500);
        }

        newItemInput.value = ''; // 清空輸入框

        // 如果在 Android 環境中，按鈕會在 getPantryItems 回調中恢復
        if (typeof Android !== 'undefined') {
            setTimeout(() => {
                addBtn.innerHTML = originalText;
                addBtn.disabled = false;
            }, 1000); // 最多等待 1 秒
        }
    }
}

// 刪除物品
function removeFromPantry(itemId) {
    console.log("Removing item:", itemId);

    // 找到要刪除的元素
    const items = document.querySelectorAll('.pantry-item');
    let itemToRemove = null;

    for (const item of items) {
        const deleteBtn = item.querySelector('.delete-btn');
        if (deleteBtn && deleteBtn.getAttribute('onclick').includes(itemId)) {
            itemToRemove = item;
            break;
        }
    }

    // 添加刪除動畫
    if (itemToRemove) {
        itemToRemove.style.transform = 'translateX(100%)';
        itemToRemove.style.opacity = '0';
    }

    // 調用 Android 方法
    setTimeout(() => {
        if (typeof Android !== 'undefined') {
            Android.removeFromPantry(itemId);
        } else {
            // 測試模式
            if (itemToRemove) {
                itemToRemove.remove();
            }

            // 檢查是否為空
            const remainingItems = document.querySelectorAll('.pantry-item');
            if (remainingItems.length === 0) {
                document.getElementById('empty-message').style.display = 'block';
            }
        }
    }, 300); // 等待動畫完成
}

// 返回主頁
function goBack() {
    window.location.href = "home.html";
}

// 加載 Pantry 物品（可以從其他地方調用）
function loadPantryItems() {
    console.log("Reloading pantry items...");
    if (typeof Android !== 'undefined') {
        Android.getPantryItems();
    }
}

function snapPhoto() {
    Android.snapPhoto();
}
