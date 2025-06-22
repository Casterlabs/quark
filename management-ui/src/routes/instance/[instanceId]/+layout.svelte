<script lang="ts">
	import { page } from '$app/state';

	import { IconHome } from '@casterlabs/heroicons-svelte';
	import { Box, Button } from '@casterlabs/ui';
</script>

<div class="flex flex-col h-screen">
	<Box class="rounded-none" sides={['right']}>
		<nav class="p-2 flex items-center space-x-1">
			<a href="/">
				<IconHome theme="mini" />
			</a>
			{#each page.url.pathname.split('/').slice(2) as part, idx}
				<span aria-hidden="true"> / </span><a
					class="underline"
					href={page.url.pathname
						.split('/')
						.slice(0, idx - 2)
						.join('/')}>{decodeURI(part)}</a
				>
			{/each}
		</nav>
	</Box>

	<div class="flex-1 overflow-x-auto overflow-y-auto p-4">
		<!-- svelte-ignore slot_element_deprecated -->
		<slot />
	</div>
</div>
