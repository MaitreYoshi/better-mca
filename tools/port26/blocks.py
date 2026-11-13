"""Blocs 26.x : use -> useItemOn/useWithoutItem, playerWillDestroy renvoie l'état, DirectionProperty, sidedSuccess, codec."""
import re, sys

USE = re.compile(r'(    @Override\n)?    public InteractionResult use\(BlockState (\w+), Level (\w+), BlockPos (\w+), Player (\w+), InteractionHand (\w+), BlockHitResult (\w+)\) \{')

def add_import(s, imp):
    if imp in s:
        return s
    return re.sub(r'^(package [^;]+;\n)', r'\1\n' + imp, s, count=1, flags=re.M)

for p in sys.argv[1:]:
    s = open(p, encoding='utf-8').read()
    o = s

    def use(m):
        st, w, pos, pl, hand, hit = m.groups()[1:]
        return ('    @Override\n'
                '    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack mcavieStack, BlockState ' + st + ', Level ' + w + ', BlockPos ' + pos
                + ', Player ' + pl + ', InteractionHand ' + hand + ', BlockHitResult ' + hit + ') {\n'
                '        return use(' + ', '.join((st, w, pos, pl, hand, hit)) + ');\n    }\n\n'
                '    @Override\n'
                '    protected InteractionResult useWithoutItem(BlockState ' + st + ', Level ' + w + ', BlockPos ' + pos + ', Player ' + pl + ', BlockHitResult ' + hit + ') {\n'
                '        return use(' + ', '.join((st, w, pos, pl, 'InteractionHand.MAIN_HAND', hit)) + ');\n    }\n\n'
                '    public InteractionResult use(BlockState ' + st + ', Level ' + w + ', BlockPos ' + pos + ', Player ' + pl + ', InteractionHand ' + hand + ', BlockHitResult ' + hit + ') {')
    s = USE.sub(use, s)
    s = re.sub(r'InteractionResult\.sidedSuccess\([^()]*(?:\([^()]*\))?\)', 'InteractionResult.SUCCESS', s)
    s = s.replace('public void playerWillDestroy(Level', 'public BlockState playerWillDestroy(Level')
    s = re.sub(r'(\n\s*)super\.playerWillDestroy\(([^;]*)\);', r'\1return super.playerWillDestroy(\2);', s)
    if 'DirectionProperty' in s:
        s = re.sub(r'\bDirectionProperty\b', 'EnumProperty<Direction>', s)
        s = s.replace('import net.minecraft.world.level.block.state.properties.EnumProperty<Direction>;\n', '')
        s = add_import(s, 'import net.minecraft.world.level.block.state.properties.EnumProperty;')
        s = add_import(s, 'import net.minecraft.core.Direction;')
    if s != o:
        open(p, 'w', encoding='utf-8', newline='\n').write(s)
        print('ok', p)
