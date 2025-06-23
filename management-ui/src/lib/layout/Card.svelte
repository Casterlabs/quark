<script lang="ts">
	let { href = '#', onclick }: { href?: string; onclick?: () => void } = $props();
</script>

<a class="cursor-pointer block w-64 h-24 m-1 rounded-sm bg-base-3 border-base-7 border hover:border-base-8 p-2" {href} {onclick}>
	<!-- svelte-ignore slot_element_deprecated -->
	<slot />
</a>
