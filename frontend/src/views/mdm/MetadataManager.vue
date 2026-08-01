<template>
  <el-card v-loading="loading">
    <template #header>元数据管理</template>
    <el-alert :title="canManage ? '可维护当前系统的主字段、子表和子字段。' : '当前账号仅可查看元数据（只读）。'" :type="canManage ? 'success' : 'info'" :closable="false" />
    <el-switch v-model="showInactive" data-test="show-inactive" active-text="显示已停用" class="show-inactive" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />

    <section v-for="objectType in visibleObjectTypes" :key="objectType.id || objectType.code" class="object-section" :class="{ 'inactive-item': !isActive(objectType) }">
      <h3>{{ objectType.name || objectType.code }} <small>{{ objectType.code }}</small>
        <el-tag v-if="!isActive(objectType)" type="info" size="small">已停用</el-tag>
        <template v-if="canManage"><el-button v-if="isActive(objectType)" :data-test="`edit-object-${objectType.code}`" link type="primary" @click="editObjectType(objectType)">编辑对象</el-button><el-button v-if="isActive(objectType)" :data-test="`deactivate-object-${objectType.code}`" link type="danger" @click="deactivateObjectTypeAction(objectType)">停用对象</el-button><el-button v-else :data-test="`reactivate-object-${objectType.code}`" link type="success" @click="reactivateObjectTypeAction(objectType)">重新启用对象</el-button></template>
      </h3>
      <el-form v-if="canManage && objectTypeTarget === objectType.code" class="editor" label-width="104px" @submit.prevent>
        <h4>编辑对象</h4>
        <el-form-item label="对象名称"><el-input v-model="objectType.name" /></el-form-item>
        <el-form-item label="对象选项"><el-checkbox v-model="objectType.approval_required">启用审批</el-checkbox><el-checkbox v-model="objectType.department_scoped">按部门管理</el-checkbox></el-form-item>
        <el-button data-test="save-object-type" type="primary" :loading="saving" @click="saveObjectType">保存对象</el-button><el-button @click="objectTypeTarget = ''">取消</el-button>
      </el-form>
      <el-table :data="visibleFields(objectType.fields)" empty-text="暂无主字段">
        <el-table-column prop="field_key" label="字段编码" />
        <el-table-column prop="field_name" label="字段名称" />
        <el-table-column prop="data_type" label="类型" />
        <el-table-column label="状态"><template #default="{ row }"><el-tag v-if="!isActive(row)" type="info" size="small">已停用</el-tag><span v-else>{{ statusLabel(row.status) }}</span></template></el-table-column>
        <el-table-column v-if="canManage" label="操作" width="150"><template #default="{ row }">
          <el-button v-if="isActive(row)" :data-test="`edit-master-${row.id}`" link type="primary" @click="editMaster(row)">编辑</el-button>
          <el-button v-if="isActive(row)" link type="danger" @click="deactivateMaster(objectType, row)">停用</el-button>
          <el-button v-else-if="isActive(objectType)" link type="success" @click="reactivateMaster(objectType, row)">重新启用</el-button>
        </template></el-table-column>
      </el-table>
      <el-button v-if="canManage && isActive(objectType)" data-test="add-master-field" link type="primary" @click="startMaster(objectType)">新增主字段</el-button>

      <el-form v-if="canManage && masterTarget === objectType.code" class="editor" label-width="88px" @submit.prevent>
        <h4>{{ masterField.id ? '编辑主字段' : '新增主字段' }}</h4>
        <FieldForm :model-value="masterField" :new-field="!masterField.id" :object-types="objectTypes" />
        <el-button data-test="save-master-field" type="primary" :loading="saving" @click="saveMaster">保存主字段</el-button>
        <el-button @click="masterTarget = ''">取消</el-button>
      </el-form>

      <h4>子表</h4>
      <div v-for="child in visibleChildTypes(objectType)" :key="child.id" class="child-section" :class="{ 'inactive-item': !isActive(child) }">
        <div class="child-heading">{{ child.name || child.code }} <small>{{ child.code }}</small>
          <el-tag v-if="!isActive(child)" type="info" size="small">已停用</el-tag>
          <template v-if="canManage"><el-button v-if="isActive(child)" link type="primary" @click="editChildType(objectType, child)">编辑子表</el-button><el-button v-if="isActive(child)" link type="danger" @click="deactivateChildTypeAction(objectType, child)">停用子表</el-button><el-button v-else-if="isActive(objectType)" link type="success" @click="reactivateChildTypeAction(objectType, child)">重新启用子表</el-button></template>
        </div>
        <el-table :data="visibleFields(child.fields)" empty-text="暂无子字段">
          <el-table-column prop="field_key" label="字段编码" /><el-table-column prop="field_name" label="字段名称" /><el-table-column prop="data_type" label="类型" />
          <el-table-column label="共享"><template #default="{ row }">{{ row.shared ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="状态"><template #default="{ row }"><el-tag v-if="!isActive(row)" type="info" size="small">已停用</el-tag><span v-else>{{ statusLabel(row.status) }}</span></template></el-table-column>
          <el-table-column v-if="canManage" label="操作" width="150"><template #default="{ row }"><el-button v-if="isActive(row)" link type="primary" @click="editChildField(objectType, child, row)">编辑</el-button><el-button v-if="isActive(row)" link type="danger" @click="deactivateChildFieldAction(objectType, child, row)">停用</el-button><el-button v-else-if="isActive(objectType) && isActive(child)" link type="success" @click="reactivateChildFieldAction(objectType, child, row)">重新启用</el-button></template></el-table-column>
        </el-table>
        <el-button v-if="canManage && isActive(objectType) && isActive(child)" :data-test="`add-child-field-${child.id}`" link type="primary" @click="startChildField(objectType, child)">新增子字段</el-button>
      </div>
      <el-button v-if="canManage && isActive(objectType)" link type="primary" @click="startChildType(objectType)">新增子表</el-button>

      <el-form v-if="canManage && childTypeTarget === objectType.code" class="editor" label-width="88px" @submit.prevent>
        <h4>{{ childType.id ? '编辑子表' : '新增子表' }}</h4>
        <el-form-item label="子表编码"><el-input v-model="childType.code" :disabled="!!childType.id" /></el-form-item>
        <el-form-item label="子表名称"><el-input v-model="childType.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="childType.sort_order" :min="0" /></el-form-item>
        <el-button data-test="save-child-type" type="primary" :loading="saving" @click="saveChildType">保存子表</el-button><el-button @click="childTypeTarget = ''">取消</el-button>
      </el-form>
      <el-form v-if="canManage && childFieldTarget.objectCode === objectType.code" class="editor" label-width="88px" @submit.prevent>
        <h4>{{ childField.id ? '编辑子字段' : '新增子字段' }}</h4>
        <FieldForm :model-value="childField" :new-field="!childField.id" :object-types="objectTypes" shared />
        <el-button data-test="save-child-field" type="primary" :loading="saving" @click="saveChildField">保存子字段</el-button><el-button @click="childFieldTarget = {}">取消</el-button>
      </el-form>
    </section>
  </el-card>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElCheckbox, ElFormItem, ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus'
import { createChildField, createChildType, createMasterField, deactivateChildField, deactivateChildType, deactivateMasterField, deactivateObjectType, listObjectTypes, reactivateChildField, reactivateChildType, reactivateMasterField, reactivateObjectType, updateChildField, updateChildType, updateMasterField, updateObjectType } from '../../api/mdm'
import { chineseError, permissionCapability, statusLabel } from '../../utils/labels'

const blankField = () => ({ field_key: '', field_name: '', data_type: 'STRING', required: false, unique_value: false, searchable: false, shared: false, max_length: null, precision_value: null, scale_value: null, reference_object_type_id: null, default_value: '', validation_rule: '', sort_order: 0 })
const FieldForm = defineComponent({
  props: { modelValue: { type: Object, required: true }, newField: Boolean, objectTypes: { type: Array, default: () => [] }, shared: Boolean }, emits: ['update:modelValue'],
  setup(props) {
    const update = (key, value) => props.modelValue[key] = value
    return () => [
      props.newField && h(ElFormItem, { label: '字段编码' }, () => h(ElInput, { modelValue: props.modelValue.field_key, 'onUpdate:modelValue': value => update('field_key', value) })),
      h(ElFormItem, { label: '字段名称' }, () => h(ElInput, { modelValue: props.modelValue.field_name, 'onUpdate:modelValue': value => update('field_name', value) })),
      h(ElFormItem, { label: '数据类型' }, () => h(ElSelect, { 'data-test': 'field-data-type', modelValue: props.modelValue.data_type, 'onUpdate:modelValue': value => update('data_type', value) }, () => ['STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'REFERENCE'].map(type => h(ElOption, { label: type, value: type })))),
      props.modelValue.data_type === 'REFERENCE' && h(ElFormItem, { label: '引用对象' }, () => h(ElSelect, { 'data-test': 'reference-object-type', modelValue: props.modelValue.reference_object_type_id, 'onUpdate:modelValue': value => update('reference_object_type_id', value) }, () => props.objectTypes.map(objectType => h(ElOption, { label: objectType.name || objectType.code, value: objectType.id })))),
      ['STRING', 'TEXT'].includes(props.modelValue.data_type) && h(ElFormItem, { label: '最大长度' }, () => h(ElInputNumber, { 'data-test': 'field-max-length', min: 1, modelValue: props.modelValue.max_length, 'onUpdate:modelValue': value => update('max_length', value) })),
      props.modelValue.data_type === 'DECIMAL' && h(ElFormItem, { label: '精度' }, () => h(ElInputNumber, { 'data-test': 'field-precision', min: 1, max: 38, modelValue: props.modelValue.precision_value, 'onUpdate:modelValue': value => update('precision_value', value) })),
      props.modelValue.data_type === 'DECIMAL' && h(ElFormItem, { label: '小数位' }, () => h(ElInputNumber, { 'data-test': 'field-scale', min: 0, max: 10, modelValue: props.modelValue.scale_value, 'onUpdate:modelValue': value => update('scale_value', value) })),
      h(ElFormItem, { label: '默认值' }, () => h(ElInput, { 'data-test': 'field-default-value', modelValue: props.modelValue.default_value, 'onUpdate:modelValue': value => update('default_value', value) })),
      h(ElFormItem, { label: '校验规则' }, () => h(ElInput, { 'data-test': 'field-validation-rule', type: 'textarea', modelValue: props.modelValue.validation_rule, 'onUpdate:modelValue': value => update('validation_rule', value) })),
      h(ElFormItem, { label: '字段选项' }, () => [h(ElCheckbox, { modelValue: props.modelValue.required, 'onUpdate:modelValue': value => update('required', value) }, () => '必填'), h(ElCheckbox, { modelValue: props.modelValue.unique_value, 'onUpdate:modelValue': value => update('unique_value', value) }, () => '唯一'), h(ElCheckbox, { modelValue: props.modelValue.searchable, 'onUpdate:modelValue': value => update('searchable', value) }, () => '可搜索'), props.shared && h(ElCheckbox, { modelValue: props.modelValue.shared, 'onUpdate:modelValue': value => update('shared', value) }, () => '共享')]),
      h(ElFormItem, { label: '排序' }, () => h(ElInputNumber, { min: 0, modelValue: props.modelValue.sort_order, 'onUpdate:modelValue': value => update('sort_order', value) })),
    ]
  },
})
const objectTypes = ref([]), showInactive = ref(false), error = ref(''), loading = ref(false), saving = ref(false)
const objectTypeTarget = ref(''), masterTarget = ref(''), childTypeTarget = ref(''), childFieldTarget = ref({})
const masterField = reactive(blankField()), childField = reactive(blankField()), childType = reactive({ id: null, code: '', name: '', sort_order: 0 })
const objectType = reactive({ id: null, code: '', name: '', approval_required: false, department_scoped: true })
const user = JSON.parse(localStorage.getItem('user') || '{}')
const canManage = computed(() => user.is_admin === true || permissionCapability('MDM_FIELD_MANAGE', 'can_edit'))
const isActive = item => !item.status || item.status.toLowerCase() === 'active'
const visibleObjectTypes = computed(() => objectTypes.value.filter(item => showInactive.value || isActive(item)))
const visibleFields = fields => (fields || []).filter(item => showInactive.value || isActive(item))
const visibleChildTypes = objectType => (objectType.child_types || []).filter(item => showInactive.value || isActive(item))
function copy(target, value, defaults = {}) {
  Object.keys(target).forEach(key => delete target[key])
  Object.assign(target, defaults, JSON.parse(JSON.stringify(value || {})))
}
async function load() { loading.value = true; error.value = ''; try { objectTypes.value = (await listObjectTypes(true)).data || [] } catch (e) { error.value = chineseError(e, '加载元数据失败') } finally { loading.value = false } }
function startMaster(object) { masterTarget.value = object.code; copy(masterField, blankField()) }
function editObjectType(value) { objectTypeTarget.value = value.code; copy(objectType, value, { id: null, code: '', name: '', approval_required: false, department_scoped: true }) }
function editMaster(field) { masterTarget.value = objectTypes.value.find(o => (o.fields || []).some(f => f.id === field.id))?.code || ''; copy(masterField, field, blankField()) }
function startChildType(object) { childTypeTarget.value = object.code; copy(childType, { id: null, code: '', name: '', sort_order: 0 }) }
function editChildType(object, value) { childTypeTarget.value = object.code; copy(childType, value, { id: null, code: '', name: '', sort_order: 0 }) }
function startChildField(object, child) { childFieldTarget.value = { objectCode: object.code, childId: child.id }; copy(childField, blankField()) }
function editChildField(object, child, field) { childFieldTarget.value = { objectCode: object.code, childId: child.id }; copy(childField, field, blankField()) }
function fieldPayload(field, includeShared = false) {
  const { id, status, field_key, shared, ...payload } = field
  const result = id ? payload : { field_key, ...payload }
  const normalized = { ...result, reference_object_type_id: field.data_type === 'REFERENCE' ? field.reference_object_type_id : null }
  return includeShared ? { ...normalized, shared: !!shared } : normalized
}
function invalid(...values) { return values.some(value => !String(value || '').trim()) }
function missingReference(field) { return field.data_type === 'REFERENCE' && (field.reference_object_type_id == null || field.reference_object_type_id === '') }
async function mutate(action, fallback) { if (!canManage.value || saving.value) return; saving.value = true; error.value = ''; try { await action(); await load() } catch (e) { error.value = chineseError(e, fallback) } finally { saving.value = false } }
async function saveObjectType() { const code = objectTypeTarget.value; if (invalid(objectType.name)) { error.value = '请填写对象名称'; return } await mutate(async () => { await updateObjectType(code, { name: objectType.name, approval_required: !!objectType.approval_required, department_scoped: !!objectType.department_scoped }); objectTypeTarget.value = '' }, '保存对象失败') }
async function saveMaster() { const code = masterTarget.value; if (invalid(masterField.field_name) || (!masterField.id && invalid(masterField.field_key))) { error.value = '请填写字段编码和字段名称'; return } if (missingReference(masterField)) { error.value = '请选择引用对象'; return } await mutate(async () => { if (masterField.id) await updateMasterField(code, masterField.id, fieldPayload(masterField)); else await createMasterField(code, fieldPayload(masterField)); masterTarget.value = '' }, '保存主字段失败') }
async function saveChildType() { const code = childTypeTarget.value; if (invalid(childType.name) || (!childType.id && invalid(childType.code))) { error.value = '请填写子表编码和子表名称'; return } await mutate(async () => { const payload = { name: childType.name, sort_order: childType.sort_order }; if (childType.id) await updateChildType(code, childType.id, payload); else await createChildType(code, { code: childType.code, ...payload }); childTypeTarget.value = '' }, '保存子表失败') }
async function saveChildField() { const { objectCode, childId } = childFieldTarget.value; if (invalid(childField.field_name) || (!childField.id && invalid(childField.field_key))) { error.value = '请填写字段编码和字段名称'; return } if (missingReference(childField)) { error.value = '请选择引用对象'; return } await mutate(async () => { if (childField.id) await updateChildField(objectCode, childId, childField.id, fieldPayload(childField, true)); else await createChildField(objectCode, childId, fieldPayload(childField, true)); childFieldTarget.value = {} }, '保存子字段失败') }
function deactivateMaster(object, field) { mutate(() => deactivateMasterField(object.code, field.id), '停用主字段失败') }
function deactivateObjectTypeAction(value) { mutate(() => deactivateObjectType(value.code), '停用对象失败') }
function deactivateChildTypeAction(object, child) { mutate(() => deactivateChildType(object.code, child.id), '停用子表失败') }
function deactivateChildFieldAction(object, child, field) { mutate(() => deactivateChildField(object.code, child.id, field.id), '停用子字段失败') }
function reactivateMaster(object, field) { mutate(() => reactivateMasterField(object.code, field.id), '重新启用主字段失败') }
function reactivateObjectTypeAction(value) { mutate(() => reactivateObjectType(value.code), '重新启用对象失败') }
function reactivateChildTypeAction(object, child) { mutate(() => reactivateChildType(object.code, child.id), '重新启用子表失败') }
function reactivateChildFieldAction(object, child, field) { mutate(() => reactivateChildField(object.code, child.id, field.id), '重新启用子字段失败') }
onMounted(load)
</script>
<style scoped>
.object-section{margin-top:22px}.object-section h3{margin-bottom:10px}.object-section small{color:#909399;font-weight:normal}.child-section{margin:14px 0 14px 18px}.child-heading{font-weight:600;margin-bottom:6px}.editor{max-width:640px;margin:12px 0;padding:14px;border:1px solid var(--el-border-color);border-radius:4px}.editor h4{margin:0 0 12px}.notice{margin-top:12px}.show-inactive{margin-top:12px}.inactive-item{opacity:.62;filter:grayscale(.3)}
</style>
