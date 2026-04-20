package com.stugram.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stugram.app.ui.search.SearchViewModel
import com.stugram.app.ui.search.toUIModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    isDarkMode: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onProfileClick: (String) -> Unit = {}
) {
    val searchViewModel: SearchViewModel = viewModel()
    val backgroundColor = if (isDarkMode) GlobalBackgroundColor else Color.White
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val glassBg = if (isDarkMode) Color.White.copy(0.12f) else Color.White.copy(0.7f)
    val glassBorder = if (isDarkMode) Color.White.copy(0.15f) else Color.Black.copy(0.1f)
    val accentBlue = Color(0xFF00A3FF)
    
    val searchQuery = searchViewModel.searchQuery
    var currentView by remember { mutableStateOf("search") } // "search", "post_detail"
    var selectedPostIndex by remember { mutableIntStateOf(0) }
    var selectedPosts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var selectedCommentsPost by remember { mutableStateOf<PostData?>(null) }

    // Search Modal state
    var isSearchModalOpen by remember { mutableStateOf(false) }

    // Filters removed: previous UI used hardcoded demo option lists. Backend taxonomy endpoints are not present.
    val activeFilterType: String? = null

    val isExploreState = searchViewModel.isShowingExploreState()
    val userDisplayList = if (isExploreState) searchViewModel.activeCreators else searchViewModel.userResults
    val postDisplayList = if (isExploreState) searchViewModel.trendingPosts else searchViewModel.postResults

    BackHandler(enabled = isSearchModalOpen || currentView == "post_detail") {
        if (currentView == "post_detail") {
            currentView = "search"
        } else if (isSearchModalOpen) {
            isSearchModalOpen = false
        }
    }

    Crossfade(targetState = currentView) { view ->
        if (view == "post_detail") {
            SearchPostDetailFeed(
                isDarkMode = isDarkMode,
                posts = selectedPosts,
                initialIndex = selectedPostIndex,
                onBack = { currentView = "search" },
                onProfileClick = onProfileClick,
                onCommentsClick = { selectedCommentsPost = it },
                onToggleLike = { searchViewModel.toggleLike(it) },
                onToggleSave = { searchViewModel.toggleSave(it) }
            )
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = rememberPullToRefreshState(),
                        isRefreshing = isRefreshing,
                        color = accentBlue,
                        containerColor = if (isDarkMode) Color(0xFF1A1A1A) else Color.White,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // --- 1. SEARCH BAR ---
                            SearchTopSection(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchViewModel.onSearchQueryChange(it) },
                                onSearchClick = { isSearchModalOpen = true },
                                isDarkMode = isDarkMode,
                                accentBlue = accentBlue,
                                glassBg = glassBg,
                                glassBorder = glassBorder,
                                contentColor = contentColor
                            )

                            // --- FILTER PANEL ---
                            FilterPanel(
                                visible = false,
                                selectedViloyat = searchViewModel.selectedViloyat,
                                selectedTuman = searchViewModel.selectedTuman,
                                selectedMaktab = searchViewModel.selectedMaktab,
                                selectedSinf = searchViewModel.selectedSinf,
                                selectedGuruh = searchViewModel.selectedGuruh,
                                activeFilterType = activeFilterType,
                                onFilterTypeClick = { },
                                onOptionSelect = { type, option ->
                                    // Filters are intentionally disabled.
                                },
                                optionsData = emptyMap(),
                                glassBg = glassBg,
                                glassBorder = glassBorder,
                                accentBlue = accentBlue
                            )

                        // --- 2. MAIN CONTENT (EXPLORE) ---
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            item(span = { GridItemSpan(2) }) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(
                                        "Faol foydalanuvchilar",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor,
                                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                                    )
                                    CreatorsLoopPager(isDarkMode, accentBlue, onProfileClick)
                                }
                            }

                            item(span = { GridItemSpan(2) }) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔥", fontSize = 18.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Ommabop videolar",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                }
                            }

                            itemsIndexed(searchViewModel.trendingPosts) { index, post ->
                                TrendingLoopItem(
                                    index = index, 
                                    post = post,
                                    isDarkMode = isDarkMode, 
                                    hasEmoji = true,
                                    modifier = Modifier
                                        .padding(
                                            start = if (index % 2 == 0) 16.dp else 0.dp,
                                            end = if (index % 2 == 1) 16.dp else 0.dp
                                        )
                                        .clickable { 
                                            selectedPosts = searchViewModel.trendingPosts
                                            selectedPostIndex = index
                                            currentView = "post_detail"
                                        }
                                )
                            }
                        }
                    }

                    // --- FULL SCREEN SEARCH MODAL ---
                    AnimatedVisibility(
                        visible = isSearchModalOpen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor)
                                .statusBarsPadding()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SearchTopSection(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchViewModel.onSearchQueryChange(it) },
                                    onSearchClick = { isSearchModalOpen = false },
                                    isDarkMode = isDarkMode,
                                    accentBlue = accentBlue,
                                    glassBg = glassBg,
                                    glassBorder = glassBorder,
                                    contentColor = contentColor,
                                    isModal = true
                                )

                                Spacer(Modifier.height(8.dp))

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // RECENT SEARCH HISTORY
                                    if (isExploreState && searchViewModel.historyResults.isNotEmpty()) {
                                        item(span = { GridItemSpan(2) }) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Oxirgi qidiruvlar", color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                TextButton(onClick = { searchViewModel.clearHistory() }) {
                                                    Text("Tozalash", color = accentBlue, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                        
                                        item(span = { GridItemSpan(2) }) {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(searchViewModel.historyResults) { item ->
                                                    Surface(
                                                        onClick = { searchViewModel.onSearchQueryChange(item.queryText) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = glassBg,
                                                        border = BorderStroke(1.dp, glassBorder)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Outlined.History, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(item.queryText, color = contentColor, fontSize = 13.sp)
                                                            IconButton(
                                                                onClick = { searchViewModel.deleteHistoryItem(item._id) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // USER RESULTS
                                    item(span = { GridItemSpan(2) }) {
                                        Text(
                                            if (isExploreState) "Tavsiya etilgan profillar" else "Profillar",
                                            color = contentColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    if (!isExploreState && searchViewModel.hasActiveFilters()) {
                                        item(span = { GridItemSpan(2) }) {
                                            SearchInfoCard(
                                                text = "Filtrlar profil natijalariga qo'llanildi. Kontent natijalari esa joriy qidiruv matni bo'yicha ko'rsatildi.",
                                                isDarkMode = isDarkMode,
                                                accentBlue = accentBlue
                                            )
                                        }
                                    }

                                    if (userDisplayList.isEmpty() && !searchViewModel.isSearchLoading) {
                                        item(span = { GridItemSpan(2) }) {
                                            SearchEmptyBlock(
                                                title = if (isExploreState) "Tavsiya etilgan profillar yo'q" else "Foydalanuvchilar topilmadi",
                                                subtitle = if (isExploreState) "Yangi profillar paydo bo'lganda shu yerda ko'rasiz" else "Boshqa so'z yoki filter bilan qayta urinib ko'ring",
                                                isDarkMode = isDarkMode
                                            )
                                        }
                                    } else {
                                        items(userDisplayList.size) { index ->
                                            val profile = userDisplayList[index]
                                            RecommendedProfileCard(
                                                profile, 
                                                isDarkMode, 
                                                accentBlue, 
                                                glassBg, 
                                                glassBorder, 
                                                onProfileClick = {
                                                    searchViewModel.recordUserSelection(profile)
                                                    onProfileClick(profile.username)
                                                    isSearchModalOpen = false
                                                },
                                                onFollowClick = { userId ->
                                                    searchViewModel.toggleFollow(userId)
                                                }
                                            )
                                        }
                                    }

                                    // POST/REEL RESULTS
                                    item(span = { GridItemSpan(2) }) {
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            if (isExploreState) "Ommabop videolar" else "Reels va postlar",
                                            color = contentColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    if (postDisplayList.isEmpty() && !searchViewModel.isSearchLoading) {
                                        item(span = { GridItemSpan(2) }) {
                                            SearchEmptyBlock(
                                                title = if (isExploreState) "Kontent topilmadi" else "Mos reels yoki post topilmadi",
                                                subtitle = if (isExploreState) "Trend kontent shu yerda ko'rinadi" else "Caption yoki tavsifga yaqin boshqa ibora bilan qidirib ko'ring",
                                                isDarkMode = isDarkMode
                                            )
                                        }
                                    } else {
                                        itemsIndexed(postDisplayList) { index, post ->
                                            TrendingLoopItem(
                                                index = index + 50, 
                                                post = post,
                                                isDarkMode = isDarkMode, 
                                                hasEmoji = false,
                                                modifier = Modifier.clickable {
                                                    selectedPosts = postDisplayList
                                                    selectedPostIndex = index
                                                    currentView = "post_detail"
                                                    isSearchModalOpen = false
                                                }
                                            )
                                        }
                                    }

                                }
                            }

                            if (searchViewModel.isSearchLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentBlue)
                                }
                            }

                            searchViewModel.searchErrorMessage?.let { errorMessage ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp)
                                ) {
                                    SearchErrorCard(
                                        message = errorMessage,
                                        isDarkMode = isDarkMode,
                                        accentBlue = accentBlue,
                                        onRetry = {
                                            searchViewModel.retrySearch()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedCommentsPost?.let { post ->
            CommentsBottomSheet(
                post = post,
                isDarkMode = isDarkMode,
                accentBlue = accentBlue,
                onDismiss = { selectedCommentsPost = null },
                onCommentAdded = {
                    post.backendId?.let { searchViewModel.handleCommentAdded(it) }
                }
            )
        }
    }
}

@Composable
fun CreatorsLoopPager(isDarkMode: Boolean, accentBlue: Color, onProfileClick: (String) -> Unit) {
    val searchViewModel: SearchViewModel = viewModel()
    val pagerState = rememberPagerState(pageCount = { searchViewModel.activeCreators.size })
    
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 40.dp),
        pageSpacing = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        val pageOffset = (
            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        ).absoluteValue

        val profile = searchViewModel.activeCreators[page]

        CreatorsLoopCard(
            profile = profile,
            isDarkMode = isDarkMode,
            accentBlue = accentBlue,
            modifier = Modifier
                .graphicsLayer {
                    val scale = lerp(
                        start = 0.85f,
                        stop = 1f,
                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    )
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(
                        start = 0.5f,
                        stop = 1f,
                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    )
                }
                .clickable { onProfileClick(profile.username) }
        )
    }
}

@Composable
fun SearchTopSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isDarkMode: Boolean,
    accentBlue: Color,
    glassBg: Color,
    glassBorder: Color,
    contentColor: Color,
    isModal: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = glassBg,
            border = BorderStroke(1.dp, glassBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = Color.Gray)
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) Text("Qidiruv...", color = Color.Gray, fontSize = 15.sp)
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = contentColor, fontSize = 15.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(accentBlue)
                    )
                }
            }
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isModal) accentBlue else glassBg)
                .border(1.dp, if (isModal) Color.Transparent else glassBorder, RoundedCornerShape(18.dp))
        ) {
            Icon(
                if (isModal) Icons.Default.Close else Icons.Default.Search,
                null,
                tint = if (isModal) Color.White else contentColor
            )
        }
    }
}

@Composable
fun FilterPanel(
    visible: Boolean,
    selectedViloyat: String?,
    selectedTuman: String?,
    selectedMaktab: String?,
    selectedSinf: String?,
    selectedGuruh: String?,
    activeFilterType: String?,
    onFilterTypeClick: (String) -> Unit,
    onOptionSelect: (String, String) -> Unit,
    optionsData: Map<String, List<String>>,
    glassBg: Color,
    glassBorder: Color,
    accentBlue: Color
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(glassBg)
                .border(1.dp, glassBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            val filterButtons = listOf(
                "Viloyat" to selectedViloyat,
                "Tuman" to selectedTuman,
                "Maktab" to selectedMaktab,
                "Sinf" to selectedSinf,
                "Guruh" to selectedGuruh
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filterButtons) { pair ->
                    FilterActionButton(
                        label = pair.second ?: pair.first,
                        isSelected = activeFilterType == pair.first,
                        hasValue = pair.second != null,
                        onClick = { onFilterTypeClick(pair.first) }
                    )
                }
            }

            AnimatedVisibility(visible = activeFilterType != null) {
                val options = optionsData[activeFilterType] ?: emptyList()
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = glassBorder)
                    LazyRow(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(options) { option ->
                            SuggestionChip(
                                label = option,
                                onClick = { onOptionSelect(activeFilterType!!, option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedProfileCard(
    profile: RecommendedProfile,
    isDarkMode: Boolean,
    accentBlue: Color,
    glassBg: Color,
    glassBorder: Color,
    onProfileClick: () -> Unit,
    onFollowClick: (String) -> Unit = {}
) {
    val searchViewModel: SearchViewModel = viewModel()
    val isPending = profile.backendId != null && searchViewModel.pendingFollowIds.contains(profile.backendId)
    val followStatus = profile.followStatus.lowercase().ifBlank {
        if (profile.isFollowed) "following" else "not_following"
    }
    val buttonText = when (followStatus) {
        "following" -> "Following"
        "requested" -> "Requested"
        "self" -> "You"
        else -> "Obuna bo'lish"
    }
    val buttonTextColor = when (followStatus) {
        "following" -> Color.White
        "requested" -> Color.White
        "self" -> Color.White
        else -> Color.Black
    }
    val buttonContainerColor = when (followStatus) {
        "following" -> Color.Black.copy(0.35f)
        "requested" -> Color.Black.copy(0.28f)
        "self" -> Color.Black.copy(0.18f)
        else -> Color.White.copy(0.9f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, glassBorder, RoundedCornerShape(26.dp))
            .clickable { onProfileClick() }
    ) {
        AppBanner(
            imageModel = profile.image,
            title = profile.name,
            modifier = Modifier.fillMaxSize(),
            isDarkMode = isDarkMode
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.Black.copy(0.3f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.2f)),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    "@${profile.username}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Surface(
                onClick = { 
                    profile.backendId?.let { onFollowClick(it) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(14.dp),
                color = buttonContainerColor,
                border = BorderStroke(1.dp, Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isPending) {
                        CircularProgressIndicator(color = buttonTextColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            buttonText,
                            color = buttonTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterActionButton(label: String, isSelected: Boolean, hasValue: Boolean, onClick: () -> Unit) {
    val accentBlue = Color(0xFF00A3FF)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentBlue.copy(0.2f) else if (hasValue) accentBlue.copy(0.1f) else Color.White.copy(0.05f),
        border = BorderStroke(1.dp, if (isSelected || hasValue) accentBlue.copy(0.5f) else Color.White.copy(0.1f)),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isSelected || hasValue) accentBlue else Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                null,
                tint = if (isSelected || hasValue) accentBlue else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(0.1f),
        border = BorderStroke(0.5.dp, Color.White.copy(0.2f)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun CreatorsLoopCard(profile: RecommendedProfile, isDarkMode: Boolean, accentBlue: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        AppBanner(
            imageModel = profile.image,
            title = profile.name,
            modifier = Modifier.fillMaxSize(),
            isDarkMode = isDarkMode
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(0.7f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppAvatar(
                        imageModel = profile.image,
                        name = profile.name,
                        username = profile.username,
                        modifier = Modifier.size(40.dp).border(2.dp, Color.White, CircleShape),
                        isDarkMode = true,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(profile.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(profile.name, color = Color.White.copy(0.8f), fontSize = 11.sp)
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            when (profile.followStatus.lowercase().ifBlank { "not_following" }) {
                                "following" -> "Following"
                                "requested" -> "Requested"
                                "self" -> "You"
                                else -> "Explore"
                            },
                            color = accentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Surface(
                color = accentBlue.copy(0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.3f))
            ) {
                Text(
                    "Faol profil", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TrendingLoopItem(index: Int, post: PostData, isDarkMode: Boolean, hasEmoji: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            AppBanner(
                imageModel = post.image,
                title = post.user,
                modifier = Modifier.fillMaxSize(),
                isDarkMode = isDarkMode,
                shape = RoundedCornerShape(24.dp)
            )
            
            Row(
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppAvatar(
                    imageModel = post.userAvatar,
                    name = post.user,
                    username = post.user,
                    modifier = Modifier.size(24.dp).border(1.dp, Color.White, CircleShape),
                    isDarkMode = true,
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(6.dp))
                Text("@${post.user.replace(" ", "_").lowercase()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f))))
                    .padding(12.dp)
            ) {
                Text(
                    post.caption, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasEmoji) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiButton("😍")
                EmojiButton("💀")
                EmojiButton("👍")
                Icon(Icons.Default.AddCircleOutline, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SearchPostDetailFeed(
    isDarkMode: Boolean,
    posts: List<PostData>,
    initialIndex: Int,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostData) -> Unit,
    onToggleLike: (PostData) -> Unit,
    onToggleSave: (PostData) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val accentBlue = Color(0xFF00A3FF)
    val postDisplayList = posts.ifEmpty { emptyList() }

    Column(modifier = Modifier.fillMaxSize().background(if (isDarkMode) Color.Black else Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (isDarkMode) Color.White else Color.Black)
            }
            Text(
                "Loops",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color.White else Color.Black
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (postDisplayList.isEmpty()) {
                item {
                    SearchEmptyBlock(
                        title = "Kontent topilmadi",
                        subtitle = "Qayta qidiruv qilib ko'ring",
                        isDarkMode = isDarkMode
                    )
                }
            } else {
                items(postDisplayList) { post ->
                    DashboardPostItem(
                        post = post,
                        accentBlue = accentBlue,
                        isDarkMode = isDarkMode,
                        onCommentsClick = onCommentsClick,
                        onProfileClick = { onProfileClick(post.user) },
                        onToggleLike = onToggleLike,
                        onToggleSave = onToggleSave
                    )
                }
            }
        }
    }

    BackHandler {
        onBack()
    }
}

@Composable
private fun SearchEmptyBlock(
    title: String,
    subtitle: String,
    isDarkMode: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isDarkMode) Color.White else Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchInfoCard(
    text: String,
    isDarkMode: Boolean,
    accentBlue: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) accentBlue.copy(alpha = 0.12f) else accentBlue.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = if (isDarkMode) Color.White else Color.Black,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SearchErrorCard(
    message: String,
    isDarkMode: Boolean,
    accentBlue: Color,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkMode) Color(0xFF171717) else Color.White,
        border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.2f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Info, null, tint = accentBlue, modifier = Modifier.size(18.dp))
            Text(
                text = message,
                color = if (isDarkMode) Color.White else Color.Black,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Qayta", color = accentBlue)
            }
        }
    }
}

@Composable
fun EmojiButton(emoji: String) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 16.sp)
        }
    }
}
