val navBarStyle by basePreferences.navigationBarStyle.collectAsState()
val navBarOpacityPercent by basePreferences.navigationBarOpacity.collectAsState()
val navBarCornerRadius by basePreferences.navigationBarCornerRadius.collectAsState()

val hazeState = rememberHazeState()

val tabs = if (isJoined || hideMangaUi) JOINED_TABS else TABS

TabNavigator(
    tab = NovelsTab,
    key = TabNavigatorKey,
) { tabNavigator ->

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Scaffold(
            startBar = {
                if (isTabletUi()) {
                    NavigationRail {
                        tabs.fastForEach {
                            NavigationRailItem(it)
                        }
                    }
                }
            },

            bottomBar = {
                if (!isTabletUi()) {
                    val bottomNavVisible by produceState(initialValue = true) {
                        showBottomNavEvent.receiveAsFlow().collectLatest {
                            value = it
                        }
                    }

                    AnimatedVisibility(
                        visible = bottomNavVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        val navBarAlpha =
                            (navBarOpacityPercent / 100f).coerceIn(0f, 1f)

                        val navBarShape = remember(navBarCornerRadius) {
                            RoundedCornerShape(
                                topStart = navBarCornerRadius.dp,
                                topEnd = navBarCornerRadius.dp,
                            )
                        }

                        val navBarTint =
                            MaterialTheme.colorScheme.surfaceContainer.copy(
                                alpha = navBarAlpha,
                            )

                        val navigationBarModifier =
                            when (navBarStyle) {
                                BasePreferences.NavigationBarStyle.SOLID -> {
                                    Modifier
                                }

                                BasePreferences.NavigationBarStyle.GLASS -> {
                                    Modifier
                                        .clip(navBarShape)
                                        .hazeEffect(state = hazeState) {
                                            blurRadius = 24.dp
                                            noiseFactor = 0.05f
                                            tints = listOf(
                                                HazeTint(navBarTint),
                                            )
                                        }
                                }
                            }

                        NavigationBar(
                            modifier = navigationBarModifier,
                            containerColor = when (navBarStyle) {
                                BasePreferences.NavigationBarStyle.SOLID ->
                                    navBarTint

                                BasePreferences.NavigationBarStyle.GLASS ->
                                    Color.Transparent
                            },
                            shape = navBarShape,
                        ) {
                            tabs.fastForEach {
                                NavigationBarItem(
                                    it,
                                    alwaysShowLabel = alwaysShowNavLabels,
                                )
                            }
                        }
                    }
                }
            },

            contentWindowInsets = WindowInsets(0),
        ) { contentPadding ->

            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .hazeSource(hazeState),
            ) {
                AnimatedContent(
                    targetState = tabNavigator.current,
                    transitionSpec = {
                        materialFadeThroughIn(
                            initialScale = 1f,
                            durationMillis = TabFadeDuration,
                        ) togetherWith materialFadeThroughOut(
                            durationMillis = TabFadeDuration,
                        )
                    },
                    label = "tabContent",
                ) {
                    tabNavigator.saveableState(
                        key = "currentTab",
                        it,
                    ) {
                        it.Content()
                    }
                }
            }
        }
    }
}